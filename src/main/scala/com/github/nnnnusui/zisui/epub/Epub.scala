package com.github.nnnnusui.zisui.epub

import java.net.URI
import java.nio.file.{FileAlreadyExistsException, FileSystems, Files, Path, Paths}
import java.util.UUID
import java.util.zip.ZipEntry

import scala.io.StdIn

object Epub extends App {
  val title = StdIn.readLine()
  val generator = new EpubTemplateGenerator(Paths.get(".."))
  if(!generator.imagesExists){
    println(s"plz insert images to: ${generator.imageDir}")
    print("and press [Enter]:")
    StdIn.readLine()
  }
  generator.write(title)
  generator.generateEpub(title)
}
class EpubTemplateGenerator(val output: Path){
  val root = output.resolve("epub")
  val mimetype = root.resolve("mimetype")
  val metaInf  = root.resolve("META-INF")
  val containerXml = metaInf.resolve("container.xml")

  val opf = root.resolve("opf.opf")
  val toc = root.resolve("toc.ncx")
  val mainHtml = root.resolve("main.html")
  val imageDir = root.resolve("image")
  generateTemplate()

  def generateTemplate(): Unit ={
    if (Files.notExists(output      )) Files.createDirectory(output)
    if (Files.notExists(root        )) Files.createDirectory(root)
    if (Files.notExists(mimetype    )) Files.createFile     (mimetype)
    if (Files.notExists(metaInf     )) Files.createDirectory(metaInf)
    if (Files.notExists(containerXml)) Files.createFile     (containerXml)
    if (Files.notExists(opf         )) Files.createFile     (opf)
    if (Files.notExists(toc         )) Files.createFile     (toc)
    if (Files.notExists(mainHtml)) Files.createFile     (mainHtml)
    if (Files.notExists(imageDir)) Files.createDirectory(imageDir)

    Files.writeString(mimetype, "application/epub+zip")
    Files.writeString(containerXml,
      s"""<?xml version="1.0" encoding="UTF-8"?>
         |<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
         |  <rootfiles>
         |    <rootfile full-path="${root.relativize(opf).mkString("/")}" media-type="application/oebps-package+xml"/>
         |  </rootfiles>
         |</container>
         |""".stripMargin)
    Files.writeString(toc,
      s"""<?xml version='1.0' encoding='utf-8'?>
         |<ncx>
         |</ncx>
         |""".stripMargin)
    println(s"generated epub template to: ${root}")
  }

  import scala.jdk.CollectionConverters._
  def imagesExists: Boolean
    = Files.list(imageDir).iterator().asScala.nonEmpty
  def write(title: String): Unit ={
    class Image(path: Path){
      val relativePathStr: String = root.relativize(path).mkString("/")
      val name: String = path.getFileName.toString.reverse.dropWhile(_ != '.').tail.reverse.mkString
    }
    val images
      = Files.list(imageDir).iterator().asScala.toList
          .map(it=> new Image(it))
    val itemTags
      = images.map(it=> s"""<item id="${it.name}" href="${it.relativePathStr}" properties="cover-image" media-type="image/jpeg"/>""")
    val imgTags
      = images.map(it=> s"""<img src="${it.relativePathStr}"/>""")

    //        |        <meta property="dcterms:modified">2019-08-07T00:49:06Z</meta>""".stripMargin
    Files.writeString(opf,
      s"""<?xml version='1.0' encoding='utf-8'?>
         |<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="uuid_id" version="3.0">
         |  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
         |    <dc:identifier id="uuid_id" opf:scheme="uuid">${UUID.randomUUID()}</dc:identifier>
         |    <dc:language>ja-JP</dc:language>
         |    <dc:title>$title</dc:title>
         |  </metadata>
         |  <manifest>
         |    ${itemTags.mkString("\n    ")}
         |    <item id="main" href="main.html" media-type="application/xhtml+xml"/>
         |    <item id="ncx"  href="${root.relativize(toc)}" media-type="application/x-dtbncx+xml"/>
         |  </manifest>
         |  <spine>
         |    <itemref idref="main"/>
         |  </spine>
         |</package>
         |""".stripMargin)
    Files.writeString(mainHtml,
      s"""<?xml version='1.0' encoding='utf-8'?>
         |<!DOCTYPE html>
         |<html xmlns="http://www.w3.org/1999/xhtml">
         |  <head>
         |    <title>$title</title>
         |  </head>
         |  <body>
         |    ${imgTags.mkString("\n    ")}
         |  </body>
         |</html>
         |""".stripMargin)
  }
  def generateEpub(fileName: String): Unit ={
    val zipPath = output.resolve(s"$fileName.epub")
    if (Files.exists(zipPath)) throw new FileAlreadyExistsException(zipPath.toString)
    val zipUri = new URI("jar", zipPath.toUri.toString, null)

    val option = Map("create" -> "true").asJava
    val fileSystem = FileSystems.newFileSystem(zipUri, option, ClassLoader.getSystemClassLoader)
    Files.walk(root)
      .filter(it=> !Files.isDirectory(it))
      .iterator().asScala.toList
      .foreach{it=>
        val paths = root.relativize(it).iterator().asScala.toSeq.map(_.toString)
        val path = fileSystem.getPath(paths.head, paths.tail:_*)
        if(path.getParent != null)
          Files.createDirectories(path.getParent)
        Files.copy(it, path)
      }
    fileSystem.close()
  }
}