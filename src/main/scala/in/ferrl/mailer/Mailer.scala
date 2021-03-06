package in.ferrl.mailer

import javax.mail._
import javax.mail.internet._
import javax.naming.{ Context, InitialContext }
import scala.xml.{ NodeSeq, Node, Elem, Text }
import java.util.Properties
import com.typesafe.config._

import akka.actor.{ Actor, ActorLogging, Props }

object Html5 extends in.ferrl.util.Html5Writer

object eMailer {
  import eMailer._

  val SMTP_HOST_KEY = "mail.smtp.host"
  val SMTP_PORT_KEY = "mail.smtp.port"

  lazy val properties: Properties = {
    val p = System.getProperties.clone.asInstanceOf[Properties]
    val config: Config = ConfigFactory.load()
    val host: String = config.getString(SMTP_HOST_KEY)
    val port: String = config.getString(SMTP_PORT_KEY)
    p.put(SMTP_HOST_KEY, host)
    p.put(SMTP_PORT_KEY, port)
    p
  }

  lazy val charSet = properties.getProperty("mail.charset") match {
    case null => "UTF-8"
    case x => x
  }

  def props(name: String) = Props(new eMailer(name))

  def buildAttachment(holder: PlusImageHolder) = {
    val part = new MimeBodyPart
    part.setFileName(holder.name)
    part.setContentID(holder.name)
    part.setDisposition(if (holder.attachment) Part.ATTACHMENT else Part.INLINE)
    part.setDataHandler(new javax.activation.DataHandler(new javax.activation.DataSource {
      def getContentType = holder.mimeType
      def getInputStream = new java.io.ByteArrayInputStream(holder.bytes)
      def getName = holder.name
      def getOutputStream = throw new java.io.IOException("Unable to write to item")
    }))
    part
  }

  def encodeHtmlBodyPart(in: NodeSeq): String = Html5.toString(firstNode(in))

  def firstNode(in: NodeSeq): Node = in match {
    case n: Node => n
    case ns => ns.toList.collect {
      case e: Elem => e
    } match {
      case Nil => if (ns.length == 0) Text("") else ns(0)
      case x :: xs => x
    }
  }

  /**
   * Prepare the message to sent to recipients
   */
  def prepareMessage(session: Session, from: From, subject: Subject, info: List[MailTypes]): MimeMessage = {
    import collection.JavaConversions._
    val message = new MimeMessage(session)

    message.setFrom(new InternetAddress(from.address))
    message.setRecipients(Message.RecipientType.TO, info.flatMap {
      case x: To => Some[Address](new InternetAddress(x.address))
      case _ => None
    }.toArray)
    message.setRecipients(Message.RecipientType.CC, info.flatMap {
      case x: CC => Some[Address](new InternetAddress(x.address))
      case _ => None
    }.toArray)
    message.setRecipients(Message.RecipientType.BCC, info.flatMap {
      case x: BCC => Some[Address](new InternetAddress(x.address))
      case _ => None
    }.toArray)
    message.setSentDate(new java.util.Date())
    message.setReplyTo(info.flatMap {
      case x: ReplyTo => Some[InternetAddress](new InternetAddress(x.address))
      case _ => None
    }.toArray)
    message.setSubject(subject.value)
    info.foreach {
      case MessageHeader(name, value) => message.addHeader(name, value)
      case _ =>
    }
    val bodyTypes = info.flatMap {
      case x: MailBodyType => Some[MailBodyType](x)
      case _ => None
    }
    bodyTypes match {
      case PlainMailBodyType(txt) :: Nil =>
        message.setText(txt)
      case _ =>
        val multiPart = new MimeMultipart("alternative")
        bodyTypes.foreach { tab =>
          val bp = buildMailBody(tab)
          multiPart.addBodyPart(bp)
        }
        message.setContent(multiPart)
    }

    // Return the message
    message
  }

  /**
   * Given a MailBodyType, convert it to a javax.mail.BodyPart. You can override this method if you
   * add custom MailBodyTypes
   */
  protected def buildMailBody(tab: MailBodyType): BodyPart = {
    val bp = new MimeBodyPart
    tab match {
      case PlainMailBodyType(txt) =>
        bp.setText(txt, "UTF-8")
      case PlainPlusBodyType(txt, charset) =>
        bp.setText(txt, charset)
      case XHTMLMailBodyType(html) =>
        bp.setContent(encodeHtmlBodyPart(html), s"text/html; charset=$charSet")
      case XHTMLPlusImages(html, img @ _*) =>
        val (attachments, images) = img.partition(_.attachment)
        val relatedMultipart = new MimeMultipart("related")
        val htmlBodyPart = new MimeBodyPart
        htmlBodyPart.setContent(encodeHtmlBodyPart(html), s"text/html; charset=$charSet")
        relatedMultipart.addBodyPart(htmlBodyPart)

        images.foreach { image =>
          relatedMultipart.addBodyPart(buildAttachment(image))
        }
        if (attachments.isEmpty) {
          bp.setContent(relatedMultipart)

        } else {
          val mixedMultipart = new MimeMultipart("mixed")
          val relatedMultipartBodypart = new MimeBodyPart
          relatedMultipartBodypart.setContent(relatedMultipart)
          mixedMultipart.addBodyPart(relatedMultipartBodypart)
          attachments.foreach { attachment =>
            mixedMultipart.addBodyPart(buildAttachment(attachment))
          }
          bp.setContent(mixedMultipart)
        }
    }
    bp
  }
}

/**
 * Mailer actor: accepts a variable
 * @name -  name of the Mailer actor
 */
class eMailer(name: String) extends Actor with ActorLogging {
  import eMailer._

  override def receive = {
    case MessageInfo(from, subject, rest) =>
      try {
        sendEmail(from, subject, rest)
        sender() ! "DONE"
      } catch {
        case e: Exception =>
          sender() ! "FAILED"
          log.error("Couldn't send mail", e)
      }
  }

  def host = hostFunc()

  var authenticator: Option[Authenticator] = None

  var hostFunc: () => String = _host _

  private def _host = properties.getProperty("mail.smtp.host") match {
    case null => "localhost"
    case s => s
  }

  def buildProps: Properties = {
    val p = properties.clone.asInstanceOf[Properties]
    p.getProperty("mail.smtp.host") match {
      case null =>
        p.put("mail.smtp.host", host)
      case _ =>
    }
    p
  }

  def sendEmail(from: From, subject: Subject, info: List[MailTypes]) {

    val session = authenticator match {
      case Some(a) => Session.getInstance(buildProps, a)
      case None => Session.getInstance(buildProps)
    }
    val subj = MimeUtility.encodeText(subject.value, "utf-8", "Q")
    Transport.send(prepareMessage(session, from, subject, info))
  }
}
