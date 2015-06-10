package me.breidenbach.asyncmailer

import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Copyright © Kevin E. Breidenbach, 5/29/15.
 */
trait Starter {
  val logger = LoggerFactory.getLogger(classOf[Starter])
}

object AsyncMailer extends Starter {
  def main(args: Array[String]): Unit = {
    MailerSupervisor.start(4, 400 milliseconds)
    try {
      sendEmail("kevin.breidenbach@gmail.com")
      sendEmail("tom.s.mackenzie@gmail.com", async = false)
      sendEmail("a@gmail.com", async = false)
      sendEmail("b@gmail.com")
      sendEmail("c@gmail.com")
      sendEmail("d@gmail.com", async = false)
      MailerSupervisor.stop()
    } catch {
      case e:MailerException =>
        logger.error(s"Error Sending Email: ${e.message}")
    }
  }

  def sendEmail(emailAddress: String, async: Boolean = true): Unit = {
    if (MailerSupervisor.sendEmail(emailAddress, async))
      logger.info(s"Successfully sent email to $emailAddress")
    else
      logger.error(s"Problem sending email to $emailAddress")
  }
}
