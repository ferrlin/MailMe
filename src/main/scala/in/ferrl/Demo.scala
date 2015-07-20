package in.ferrl

object BasicDemo extends App {

  import in.ferrl.mailer._
  import scala.concurrent
  import scala.concurrent.duration._

  PostOffice.init(1, 1.second)

  PostOffice.sendMail(
    From(address = "test@test.com"),
    Subject(value = "This is  test email"),
    To("recipient@nowhere.com"),
    PlainMailBodyType("Here is some plain text."))(async = true)

  PostOffice.stop()
}