package com.doanduyhai.elevator

import java.io.File

trait FileContentReader {

  def readContentFromFile(filename: String):String =  {
    val classLoader = getClass().getClassLoader()
    val file = new File(classLoader.getResource(filename).getFile())
    val source = scala.io.Source.fromFile(file.getAbsolutePath)
    val lines = try source.getLines mkString "\n" finally source.close()
    lines
  }
}
