package in.ferrl

object BuldEmailDemo extends App {

  import in.ferrl.mailer._
  import in.ferrl.mailer.PostOffice
  import scala.concurrent.duration._

  PostOffice.init(1, 1.second)

  PostOffice.sendMail(
    From(address = "bulkemailsender@test.com"),
    Subject(value = "This is  test email"),
    To("recipient@nowhere.com"),
    To("info@nowhere.com"),
    To("john@ferrl.in"),
    To("batman@cave.com"),
    CC("seen@ok.net"),
    BCC("president@use.gov"),
    BCC("secretary@use.gove"),
    XHTMLMailBodyType(<html><body><H1>Helow in HTML!!!</H1></body></html>))(async = true)

  PostOffice.stop()
}
