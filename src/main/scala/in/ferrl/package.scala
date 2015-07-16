package in.ferrl
import xml.{ Text, Elem, Node, NodeSeq }

package object mailer {

  sealed abstract class MailTypes
  /**
   * Add message headers to outgoing messages
   */
  final case class MessageHeader(name: String, value: String) extends MailTypes
  abstract class MailBodyType extends MailTypes
  final case class PlusImageHolder(name: String, mimeType: String, bytes: Array[Byte], attachment: Boolean = false)

  /**
   * Represents a text/plain mail body. The given text will
   * be encoded as UTF-8 when sent
   */
  final case class PlainMailBodyType(text: String) extends MailBodyType

  /**
   * Represents a text/plain mail body that is encoded with the specified charset
   */
  final case class PlainPlusBodyType(text: String, charset: String) extends MailBodyType
  final case class XHTMLMailBodyType(text: NodeSeq) extends MailBodyType
  final case class XHTMLPlusImages(text: NodeSeq, items: PlusImageHolder*) extends MailBodyType

  sealed abstract class RoutingTypes extends MailTypes
  sealed abstract class AddressTypes extends RoutingTypes {
    def address: String
    def name: Option[String]
  }

  final case class From(address: String, name: Option[String] = None) extends AddressTypes
  final case class To(address: String, name: Option[String] = None) extends AddressTypes
  final case class CC(address: String, name: Option[String] = None) extends AddressTypes
  final case class Subject(value: String) extends RoutingTypes
  final case class BCC(address: String, name: Option[String] = None) extends AddressTypes
  final case class ReplyTo(address: String, name: Option[String] = None) extends AddressTypes

  final case class MessageInfo(from: From, subject: Subject, info: List[MailTypes])
}