package com.github.al.assad

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}
import scala.language.{implicitConversions, postfixOps}
import scala.util.Random

package object akka {

  implicit class AwaitableFuture[T](future: Future[T]) {
    def waitResult(timeout: Duration = 3.seconds): T = Await.result(future, timeout)
  }

  def Use[T, R](resource: => T)(func: T => R): R = func(resource)

  def sleep(duration: Duration): Unit = Thread.sleep(duration.toMillis)

  def sleep(milliseconds: Long): Unit = Thread.sleep(milliseconds)

  def sleepForever(): Unit = sleep(1.day)

  implicit def durationToMillis(duration: Duration): Int = duration.toMillis.toInt

  def assertUnit(cond: => Boolean, interval: Duration = 500 milliseconds): Future[Unit] =
    Future {
      assert(cond)
    } recoverWith { case _ =>
      sleep(interval)
      assertUnit(cond)
    }

  val rand = new Random


  def uuidShort(bit: Int = 8): String = {
    val uuid = java.util.UUID.randomUUID()
    val uuidStr = uuid.toString.replace("-", "")
    uuidStr.substring(0, bit)
  }
}
