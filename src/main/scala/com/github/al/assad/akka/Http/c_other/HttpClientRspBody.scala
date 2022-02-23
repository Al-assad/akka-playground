package com.github.al.assad.akka.Http.c_other

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success}

object HttpClientRspBody extends App {
  implicit val system = ActorSystem(Behaviors.empty, "client-request")
  implicit val executionContext = system.executionContext


  val resFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://hs.assad.site:32241/jobmanager/metrics?get=Status.JVM.Memory.Heap.Used,Status.JVM.Memory.Heap.Max,Status.JVM.Memory.Metaspace.Used,Status.JVM.Memory.Metaspace.Max,Status.JVM.Memory.Heap.Committed,Status.JVM.Memory.Heap.Used,Status.JVM.Memory.Heap.Max,Status.JVM.Memory.NonHeap.Committed,Status.JVM.Memory.NonHeap.Used,Status.JVM.Memory.NonHeap.Max,Status.JVM.Memory.Direct.Count,Status.JVM.Memory.Direct.MemoryUsed,Status.JVM.Memory.Direct.TotalCapacity,Status.JVM.Memory.Mapped.Count,Status.JVM.Memory.Mapped.MemoryUsed,Status.JVM.Memory.Mapped.TotalCapacity,Status.JVM.GarbageCollector.MarkSweepCompact.Time,Status.JVM.GarbageCollector.Copy.Count,Status.JVM.GarbageCollector.Copy.Time,Status.JVM.GarbageCollector.MarkSweepCompact.Count"))
  resFuture onComplete {
    case Success(res) => println(res.entity.toStrict(5.seconds).map(_.data.utf8String))
    case Failure(exception) => println(exception)
  }


}
