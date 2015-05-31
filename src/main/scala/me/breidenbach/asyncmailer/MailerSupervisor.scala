package me.breidenbach.asyncmailer

import akka.actor._
import akka.pattern.ask
import akka.routing.{ActorRefRoutee, Router, RoundRobinRoutingLogic}
import akka.util.Timeout
import me.breidenbach.persistence.Persister

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

/**
*  Copyright © Kevin E. Breidenbach, 5/26/15.
*/
object MailerSupervisor {

  private[asyncmailer] sealed trait Request
  private[asyncmailer] case class SendEmail(emailAddress: String, seq: Long, respondToSender: Boolean) extends Request
  private[asyncmailer] case class ProcessEmail(emailAddress: String, seq: Long) extends Request

  private[asyncmailer] sealed trait Response
  private[asyncmailer] case class Ack(seq: Long) extends Response
  private[asyncmailer] case class Nack(error: Error) extends Response

  private case class MailerStatus(started: Boolean = false) {
    def changeStatus(): MailerStatus = MailerStatus(!started)
  }

  private var status = MailerStatus()
  private var supervisor: ActorRef = _
  private var waitTime: FiniteDuration = _
  private implicit var timeout: Timeout = _
  private val system = ActorSystem("Mailer_Supervisor_System")
  private var _seqCounter = 0L
  private def nextSeq = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }

  def start(mailerCount: Int, waitTimeout: FiniteDuration): Unit = {
    if (status.started) throw MailerException("Mailer Already Started!")
    else {
      waitTime = waitTimeout
      timeout = Timeout.durationToTimeout(waitTime)
      supervisor = system.actorOf(Props(new MailerSupervisor(mailerCount)))
      status = status.changeStatus()
    }
  }

  def stop(): Unit = {
    if (status.started) {
      system.shutdown()
      status = status.changeStatus()
    }
  }

  def sendEmail(emailAddress: String, async: Boolean = true): Boolean = {
    if (!status.started)
      throw MailerException("Cannot Send Email... Mailer Not Started")
    else {
      if (async) {
        supervisor ! SendEmail(emailAddress, nextSeq, respondToSender = false)
        true
      } else {
        val response: Future[Any] = supervisor ? SendEmail(emailAddress, nextSeq, respondToSender = true)
        try {
          // blocking sucks, but as a web java web app will use this, it needs to block {sorta-kinda}
          blocking {
            Await.result(response, waitTime)
            /*response.onSuccess({
              case a => return true
            })*/
            true
          }
        } catch {
          case _: Exception =>
            throw MailerException("Unable to Send Message")
        }
      }
    }
  }

  def isStarted = status.started
}

class MailerSupervisor(mailerCount: Int) extends Actor with ActorLogging {
  import MailerSupervisor._
  import Persister._

  private case object CheckResponses
  private val persisterName = "requestPersister"
  private val persister = context.actorOf(Props(new Persister("mailer-persister")), persisterName)
  context.system.scheduler.schedule(100 milliseconds, 100 milliseconds, self, CheckResponses)

  private var sequenceToSenderEmailerPending = Map.empty[Long, String]
  private var sequenceToSenderPersisterPending = Map.empty[Long, (ActorRef, String, Boolean)]

  private var _emailerCounter = 0L
  private def nextEmailerId = {
    val ret = _emailerCounter
    _emailerCounter += 1
    ret
  }

  val router = {
    val routees = Vector.fill(mailerCount) {
      val name = s"Emailer-$nextEmailerId"
      val routee = context.actorOf(Props(new Emailer(name)), name)
      context watch routee
      ActorRefRoutee(routee)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  override def receive = {
    case SendEmail(emailAddress, seq, respondToSender) => handleSendEmail(emailAddress, seq, respondToSender)
    case Persisted(seq) => handlePersisted(seq)
    case Ack(seq) => handleAck(seq)
    case CheckResponses => handleCheckResponses()
    case state: State => handleState(state)
  }

  private def handleSendEmail(emailAddress: String, seq: Long, respondToSender: Boolean): Unit = {
    sequenceToSenderPersisterPending += (seq -> (sender(), emailAddress, respondToSender))
    persister ! Event(seq, emailAddress)
    log.debug(s"sent message for persistence, seq: $seq, email: $emailAddress")

  }

  private def handlePersisted(seq: Long): Unit = {
    sequenceToSenderPersisterPending.get(seq).foreach {
      case (actor, emailAddress, respondToSender) =>
        if (respondToSender) actor ! Ack(seq)
        router.route(ProcessEmail(emailAddress, seq), self)
        sequenceToSenderEmailerPending += (seq -> emailAddress)
        sequenceToSenderPersisterPending -= seq
    }
    log.debug(s"persisted notification for seq: $seq")
  }

  private def handleAck(seq: Long): Unit = {
    sequenceToSenderEmailerPending.get(seq).foreach (
      entry => {
        sequenceToSenderEmailerPending -= seq
        persister ! Remove(seq)
      })

    log.debug(s"ack received, email sent for seq: $seq")
  }

  private def handleCheckResponses(): Unit = {
    sequenceToSenderPersisterPending.foreach {
      case (seq, (_, emailAddress, _)) =>
        persister ! Event(seq, emailAddress)
        log.debug(s"resent message to persistence with seq: $seq, email: $emailAddress")
    }
    sequenceToSenderEmailerPending.foreach {
      case (seq, emailAddress) =>
        router.route(ProcessEmail(emailAddress, seq), self)
        log.debug(s"resent message to emailer with seq: $seq, email: $emailAddress")
    }
  }

  private def handleState(state: State): Unit = {
    state.state.foreach{ case(seq, emailAddress) =>
      sequenceToSenderEmailerPending += (seq -> emailAddress)
    }
    log.debug(s"received state with ${state.state.size} entries")
  }
}


