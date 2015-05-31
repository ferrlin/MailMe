package me.breidenbach.asyncmailer

/**
 * Copyright © Kevin E. Breidenbach, 5/26/15.
 */
case class MailerException(message: String, cause: Throwable = null) extends Error(message, cause)
