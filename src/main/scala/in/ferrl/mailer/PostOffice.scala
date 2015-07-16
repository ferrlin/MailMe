package in.ferrl.mailer

import akka.actor.{ Actor, ActorSystem, ActorLogging, Props, ActorRef, Terminated }
import akka.routing.{ ActorRefRoutee, Router, RoundRobinRoutingLogic }
import akka.pattern.ask
import scala.concurrent.Await
import akka.util.Timeout

object PostOffice {

  import scala.concurrent._
  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.language.postfixOps

  implicit val timeout: Timeout = 1 second

  val system = ActorSystem("mail-system")
  var postOffice: ActorRef = _

  def init(mailerCount: Int, waitTimeout: FiniteDuration): ActorRef = {
    postOffice = system.actorOf(Props(new PostOffice(mailerCount)), "post-office")
    postOffice
  }

  def stop() {
    system.shutdown()
  }

  def sendEmail(from: From, subject: Subject, rest: MailTypes*) {
    Await.result(postOffice ? MessageInfo(from, subject, rest.toList), 1 second)
  }

  def sendEmailAsync(from: From, subject: Subject, rest: MailTypes*) {
    postOffice ! MessageInfo(from, subject, rest.toList)
  }
}

class PostOffice(mailerCount: Int = 1) extends Actor with ActorLogging {
  import in.ferrl.mailer.eMailer
  var counter = 0L

  def generateRouterId = {
    val ret = counter
    counter += 1
    ret
  }

  var router = {
    val routees = Vector.fill(mailerCount) {
      val name = s"Mailer-$generateRouterId"
      val routee = context.actorOf(Props(new eMailer(name)), name)
      context watch routee
      ActorRefRoutee(routee)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  override def receive = {
    case message: MessageInfo =>
      router.route(message, sender())
    case Terminated(a) =>
      router = router.removeRoutee(a)
      val name = s"Mailer-$generateRouterId"
      val r = context.actorOf(Props(new eMailer(name)), name)
      context watch r
      router = router.addRoutee(r)
  }
}