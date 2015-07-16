/*
 * Copyright 2010-2011 WorldWide Conferencing, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package in.ferrl
package util

import scala.xml._
import java.io._

trait Html5Writer {
  /**
   * Write the attributes in HTML5 valid format
   * @param m the attributes
   * @param writer the place to write the attribute
   */
  protected def writeAttributes(m: MetaData, writer: Writer) {
    m match {
      case null =>
      case Null =>
      case md if (null eq md.value) => writeAttributes(md.next, writer)
      case up: UnprefixedAttribute => {
        writer.append(' ')
        writer.append(up.key)
        val v = up.value
        writer.append("=\"")
        val str = v.text
        var pos = 0
        val len = str.length
        while (pos < len) {
          str.charAt(pos) match {
            case '"' => writer.append("&quot;")
            case '<' => writer.append("&lt;")
            case '&' if str.indexOf(';', pos) >= 0 => writer.append("&amp;")
            case c if c >= ' ' && c.toInt <= 127 => writer.append(c)
            case c if c == '\u0085' =>
            case c => {
              val str = Integer.toHexString(c)
              writer.append("&#x")
              writer.append("0000".substring(str.length))
              writer.append(str)
              writer.append(';')
            }
          }

          pos += 1
        }

        writer.append('"')

        writeAttributes(up.next, writer)
      }

      case pa: PrefixedAttribute => {
        writer.append(' ')
        writer.append(pa.pre)
        writer.append(':')
        writer.append(pa.key)
        val v = pa.value
        if ((v ne null) && !v.isEmpty) {
          writer.append("=\"")
          val str = v.text
          var pos = 0
          val len = str.length
          while (pos < len) {
            str.charAt(pos) match {
              case '"' => writer.append("&quot;")
              case '<' => writer.append("&lt;")
              case '&' if str.indexOf(';', pos) >= 0 => writer.append("&amp;")
              case c if c >= ' ' && c.toInt <= 127 => writer.append(c)
              case c if c == '\u0085' =>
              case c => {
                val str = Integer.toHexString(c)
                writer.append("&#x")
                writer.append("0000".substring(str.length))
                writer.append(str)
                writer.append(';')
              }
            }

            pos += 1
          }

          writer.append('"')
        }

        writeAttributes(pa.next, writer)
      }

      case x => writeAttributes(x.next, writer)
    }
  }

  /**
   * Escape text data
   * @param str the String to escape
   * @param the place to send the escaped characters
   */
  protected def escape(str: String, sb: Writer, reverse: Boolean) {
    val len = str.length
    var pos = 0
    while (pos < len) {
      str.charAt(pos) match {
        case '<' => sb.append("&lt;")
        case '>' => sb.append("&gt;")
        case '&' => sb.append("&amp;")
        case '"' => sb.append("&quot;")
        case '\n' => sb.append('\n')
        case '\r' => sb.append('\r')
        case '\t' => sb.append('\t')
        case c =>
          if (reverse) {
            HtmlEntities.revMap.get(c) match {
              case Some(str) => {
                sb.append('&')
                sb.append(str)
                sb.append(';')
              }
              case _ =>
                if (c >= ' ' &&
                  c != '\u0085' &&
                  !(c >= '\u007f' && c <= '\u0095')) sb.append(c)
            }
          } else {
            if (c >= ' ' &&
              c != '\u0085' &&
              !(c >= '\u007f' && c <= '\u0095')) sb.append(c)
          }
      }

      pos += 1
    }
  }

  /**
   * Convert a Node to a properly encoded Html5 String
   */
  def toString(x: Node): String = {
    val sr = new StringWriter()
    write(x, sr, false, true)
    sr.toString()
  }

  /**
   * Write the Node out as valid HTML5
   *
   * @param x the node to write out
   * @param writer the place to send the node
   * @param stripComment should comments be stripped from output?
   */
  def write(x: Node, writer: Writer, stripComment: Boolean, convertAmp: Boolean): Unit = {
    x match {
      case Text(str) => escape(str, writer, !convertAmp)

      case PCData(data) => {
        writer.append("<![CDATA[")
        writer.append(data)
        writer.append("]]>")
      }

      case scala.xml.PCData(data) => {
        writer.append("<![CDATA[")
        writer.append(data)
        writer.append("]]>")
      }

      case Unparsed(data) => writer.append(data)

      case a: Atom[_] if a.getClass eq classOf[Atom[_]] =>
        escape(a.data.toString, writer, !convertAmp)

      case Comment(comment) if !stripComment => {
        writer.append("<!--")
        writer.append(comment)
        writer.append("-->")
      }

      case er: EntityRef if convertAmp =>
        HtmlEntities.entMap.get(er.entityName) match {
          case Some(chr) if chr.toInt >= 128 => writer.append(chr)
          case _ => {
            val sb = new StringBuilder()
            er.buildString(sb)
            writer.append(sb)
          }
        }

      case er: EntityRef =>
        val sb = new StringBuilder()
        er.buildString(sb)
        writer.append(sb)

      case x: SpecialNode => {
        val sb = new StringBuilder()
        x.buildString(sb)
        writer.append(sb)
      }

      case g: Group =>
        for (c <- g.nodes)
          write(c, writer, stripComment, convertAmp)

      case e: Elem if (null eq e.prefix) &&
        Html5Constants.nonReplaceable_?(e.label) => {
        writer.append('<')
        writer.append(e.label)
        writeAttributes(e.attributes, writer)
        writer.append(">")
        e.child match {
          case null =>
          case seq => seq.foreach {
            case Text(str) => writer.append(str)
            case pc: PCData => {
              val sb = new StringBuilder()
              pc.buildString(sb)
              writer.append(sb)
            }
            case pc: scala.xml.PCData => {
              val sb = new StringBuilder()
              pc.buildString(sb)
              writer.append(sb)
            }
            case Unparsed(text) => writer.append(text)
            case a: Atom[_] if a.getClass eq classOf[Atom[_]] =>
              writer.append(a.data.toString)

            case _ =>
          }
        }
        writer.append("</")
        writer.append(e.label)
        writer.append('>')
      }

      case e: Elem if (null eq e.prefix) &&
        Html5Constants.voidTag_?(e.label) => {
        writer.append('<')
        writer.append(e.label)
        writeAttributes(e.attributes, writer)
        writer.append(">")
      }

      /*
      case e: Elem if ((e.child eq null) || e.child.isEmpty) => {
        writer.append('<')
        if (null ne e.prefix) {
          writer.append(e.prefix)
          writer.append(':')
        }
        writer.append(e.label)
        writeAttributes(e.attributes, writer)
        writer.append(" />")
      }*/

      case e: Elem => {
        writer.append('<')
        if (null ne e.prefix) {
          writer.append(e.prefix)
          writer.append(':')
        }
        writer.append(e.label)
        writeAttributes(e.attributes, writer)
        writer.append(">")
        e.child.foreach(write(_, writer, stripComment, convertAmp))
        writer.append("</")
        if (null ne e.prefix) {
          writer.append(e.prefix)
          writer.append(':')
        }
        writer.append(e.label)
        writer.append('>')
      }

      case _ => // dunno what it is, but ignore it
    }
  }
}

object Html5Constants {
  val voidTags: Set[String] = Set("area",
    "base",
    "br",
    "col",
    "command",
    "embed",
    "hr",
    "img",
    "input",
    "keygen",
    "link",
    "meta",
    "param",
    "source",
    "wbr")

  /**
   * Is the tag a void tag?
   */
  def voidTag_?(t: String): Boolean = voidTags.contains(t.toLowerCase)

  /**
   * Is the tag a non-replaceable tag?
   */
  def nonReplaceable_?(t: String): Boolean =
    (t equalsIgnoreCase "script") ||
      (t equalsIgnoreCase "style")
}