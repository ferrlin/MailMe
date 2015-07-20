# Async Mailer

A simple example showing asynchronous mailing with persistent backup

## Structure

There are three main files that are given
- `persistence/Persister.scala`
- `asyncmailer/Emailer.scala`
- `asyncmailer/MailerSupervisor.scala`

Additionally there is a wrapper which allows Spring based systems to pull in this component as a @Bean
- `asyncmailer/MailSupervisorControl.scala`

Lastly there is a simple main object which I've used for testing in lieu of AkkaTests (which I will add soon)
- `asyncmailer/AsyncMailer.scala`


## Using the library

The methods in the `MailerSupervisor` object, or the `MailSupervisorControl.scala` class (if you're accessing through Spring) has the methods used to control the system:

- `start(mailCount: Int, waitTimeout: FiniteDuration)`
will start the Akka system.
`mailCount` is the number of mailers you wish to have. One is sufficient for most systems, but for high load make this larger.
`waitTimeout` is for is you decide to send mail synchronously (see below) and is the timeout for waiting for a response

- `stop()`
will stop the emailer and it will need to be restarted with the start method before sending any more emails

- `isStarted`
will let you know if the service is running

- `sendEmail(emailAddress: String, async: Boolean)`
is used to send an email.
`emailAddress` is obvious
`async` should be obvious. If going through the `MailerSupervisor` object then this has a default value of `true` and can be ignored unless you want sychronous responses. If you are using the `MailSupervisorControl.scala` then there are polymorphic methods which is useful for Java clients

## Synchronous Behavior

The library offers a synchronous behavior. This does not mean that the service has sent the email, just that it has persisted the email request, which guarantees it will be sent at some point int he future.

Async will return as soon as the request is sent, which is most cases is sufficient.

If the persister fails before it receives the message, it will automatically be restarted through the Akka supervision system. Additionally, before sending the message to the persister the `MailerSupervisor` caches it and every 100ms resends any cached requests to the persister. When a `Persisted` message is sent from the persister and is received by the mailer supervisor it is removed from the cache.


# MailMe
