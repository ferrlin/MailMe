package in.ferrl.mailer

import akka.actor.{ Actor, ActorSystem, ActorLogging, Props, ActorRef, Terminated }
import akka.routing.{ ActorRefRoutee, Router, RoundRobinRoutingLogic }
import akka.pattern.ask
import scala.concurrent.Await
import akka.util.Timeout

case class Status(started: Boolean = false) {
  def toggle: Status = Status(!started)
}

object PostOffice {

  import scala.concurrent._
  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.language.postfixOps

  implicit val timeout: Timeout = 1 second

  val system = ActorSystem("mail-system")
  var postOffice: ActorRef = _
  var status: Status = Status()

  def init(mailerCount: Int, waitTimeout: FiniteDuration) {
    if (!status.started) {
      postOffice = system.actorOf(Props(new PostOffice(mailerCount)), "post-office")
      status = status.toggle
    }
  }

  def stop() {
    if (status.started) {
      system.shutdown()
      status = status.toggle
    }
  }

  def sendMail(from: From, subject: Subject, rest: MailTypes*)(implicit async: Boolean = true) {
    if (status.started) {
      if (!async) {
        Await.result(postOffice ? MessageInfo(from, subject, rest.toList), 1 second)
      } else {
        postOffice ! MessageInfo(from, subject, rest.toList)
      }
    } else
      throw new Error("Post office is not initialized")
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