package services.xmp

import java.io.{ByteArrayOutputStream, InputStream}

import org.apache.sanselan.formats.jpeg.xmp.JpegXmpRewriter

class JpegXmpWriter {

  def insertXmp(xmpXml: String, src: InputStream): Unit = {
      val writer = new JpegXmpRewriter()
      val os = new ByteArrayOutputStream()
      writer.updateXmpXml(src, os, xmpXml)
  }

}
