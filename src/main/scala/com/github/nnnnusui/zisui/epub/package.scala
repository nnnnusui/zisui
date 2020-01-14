package com.github.nnnnusui.zisui

import java.nio.file.Path

package object epub {
  implicit class RichPath(val src: Path){
    import scala.jdk.CollectionConverters._
    def mkString(sep: String): String = src.iterator().asScala.mkString(sep)
  }
}
