# MailMe

A simple smtp client built with scala and akka using java mail 1.5.

## Requirements

    This assumes that your are using:

    - jdk8
    - scala 2.11.x

    You would also need a smtp server. For debugging purposes, I would recommend installing `mocksmtp`. You can find it [here][1].
    In the application.conf, I've added the configurations with their defaults: 

    mail.smtp.host = "localhost"
    mail.smtp.port = 1025

    These are the defaults when you start `mocksmtp`. Update these configurations to suit your needs.

## Structure

There are two main files defined here. These are:
- `mailer/Mailer.scala` 
- `mailer/PostOffice.scala`


Lastly there are demos which I've used for testing in lieu of AkkaTests (which  will be  added soon)
- `Demo.scala`
- `BulkEmailDemo.scala`


## Using the library

The class PostOffice has the methods for controlling the system. To use the library, you need to initialize the PostOffice by calling the `init` and  passing the mailer(routee) count and timeout duration as parameters.

- `init(mailerCount: Int, timeout: FiniteDuration)` - is the full signature of the init method. Bypassing this call will throw Error whenenver you are using the api. 

The api for sending mail is :

- `sendMail(from: From, subject: Subject, rest: MailTypes*)(implicit async = true)`  - By default, you are sending mail asynchronously provided by the akka framework. 

If for some reason you want it synchrounously, you need to set `async=false`.

To be able to use this effectively, one must understand the data types used for constructing the message.
    `From(address: String, name: Option[String])` - is to represent the sender of the mail.

    `Subject(value: String)` - the subject of the mail to be sent.

    `MailTypes*` - this can be any of the following that extends Mailtypes. Refer to the the file package.scala under in/ferrl directory.

    An example on how to construct and send a mail can be found in either of the demo classes as described above.

- `stop()` - for stopping the ActorSystem created after initialization.

[1]: http://www.mocksmtpapp.com/
