package me.breidenbach.asyncmailer

import org.springframework.stereotype.Service

import scala.concurrent.duration.FiniteDuration

/**
 * Copyright © Kevin E. Breidenbach, 5/29/15.
 */
// A wrapper to make it easy for Spring systems to use it
@Service("amMailSupervisor")
class MailSupervisorControl {
  val supervisor = MailerSupervisor

  def start(mailCount: Int, waitTimeout: FiniteDuration): Unit = {
    supervisor.start(mailCount, waitTimeout)
  }

  // Using polymorphic approach as poor old Java clients can use default parameters yet
  def sendEmail(emailAddress: String): Boolean = {
    sendEmail(emailAddress, async = true)
  }

  def sendEmail(emailAddress: String, async: Boolean): Boolean = {
    supervisor.sendEmail(emailAddress, async)
  }

  def isStarted = supervisor.isStarted

  // use this for "destroy-method" in spring bean configuration
  def stop() = supervisor.stop()
}
