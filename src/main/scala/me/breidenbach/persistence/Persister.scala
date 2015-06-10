package me.breidenbach.persistence

import akka.actor.ActorLogging
import akka.persistence._

import scala.language.postfixOps
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Copyright © Kevin E. Breidenbach, 5/27/15.
 */

object Persister {
  sealed trait PersistenceMessage
  case class Event(seq: Long, data: String) extends PersistenceMessage
  case class Remove(seq: Long) extends PersistenceMessage
  case class Persisted(seq: Long) extends PersistenceMessage
  case object Recover extends PersistenceMessage
  case object SnapshotNow extends PersistenceMessage
  case class State(state: Map[Long, String] = Map.empty) extends PersistenceMessage {
    def addState(seq: Long, data: String): State = State(state + (seq -> data))
    def removeState(seq: Long): State = State(state - seq)
  }
}

class Persister(id: String) extends PersistentActor with ActorLogging {
  import Persister._

  private var state = State()
  context.system.scheduler.schedule(1 minute, 1 minute, self, SnapshotNow)

  override def receiveRecover: Receive = {
    case event: Event =>
      state = state.addState(event.seq, event.data)
      log.debug(s"persister recovering event received with seq: ${event.seq}, data: ${event.data}")
    case remove: Remove =>
      state = state.removeState(remove.seq)
      log.debug(s"persister recovering remove received with seq: ${remove.seq}")
    case SnapshotOffer(lastSequence, snapshot: State) =>
      state = snapshot
      log.debug(s"persister recovering snapshot received with ${state.state.size} messages")
    case RecoveryCompleted =>
      if (state.state.isEmpty) cleanUp() else context.parent ! State(state.state)
      log.debug("recovery completed... starting up")
    case RecoveryFailure =>
      log.error("error recovering, cleaning up")
  }

  override def receiveCommand: Receive = {
    case event: Event =>
      persist(event) { e =>
        state = state.addState(e.seq, e.data)
        sender ! Persisted(e.seq)
        log.debug(s"persisted seq: ${e.seq}, data: ${e.data}")
      }
    case remove: Remove =>
      persist(remove) { r =>
        state = state.removeState(r.seq)
        if (state.state.isEmpty) cleanUp()
        log.debug(s"removing seq: ${r.seq}")
      }
    case Recover =>
      sender ! State(state.state)
      log.debug(s"recover requested and snapshot sent")
    case SnapshotNow =>
      if (state.state.isEmpty) cleanUp() else saveSnapshot(state)
      log.debug("snapshot completed")
    case SaveSnapshotSuccess(metadata) =>
      deleteMessages(toSequenceNr = lastSequenceNr, permanent = true)
      log.debug(s"snapshot successful, deleting journal upto snapshot sequence number $lastSequenceNr")
  }

  override def persistenceId: String = id

  override def postStop(): Unit = {
    log.info("PERSISTER HAS SHUT DOWN. CLEANING UP IF NECESSARY")
    if (state.state.isEmpty) cleanUp()
  }

  private def cleanUp(): Unit = {
    val criteria: SnapshotSelectionCriteria = SnapshotSelectionCriteria(maxSequenceNr = lastSequenceNr)
    deleteMessages(toSequenceNr = lastSequenceNr, permanent = true)
    log.debug(s"removing messages up to seq: $lastSequenceNr")

    deleteSnapshots(criteria)
    log.debug(s"removing snapshots up to seq: $lastSequenceNr")
  }
}
