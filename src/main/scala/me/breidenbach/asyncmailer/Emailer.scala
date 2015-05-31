package me.breidenbach.asyncmailer

import akka.actor.Actor
import org.slf4j.LoggerFactory

/**
 * Copyright © Kevin E. Breidenbach, 5/26/15.
 */

private [asyncmailer] object Emailer {
  val logger = LoggerFactory.getLogger(classOf[Emailer])

  def processEmail(emailAddress: String, mailerName: String): Unit = {
    logger.error(s"$mailerName is sending email to $emailAddress")
  }
}

class Emailer(name: String) extends Actor {
  import Emailer._
  import MailerSupervisor._

  override def receive: Receive = {
    case ProcessEmail(emailAddress, seq) =>
      processEmail(emailAddress, name)
      sender ! Ack(seq)
  }
}
