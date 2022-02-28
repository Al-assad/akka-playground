package com.github.al.assad.akka.Http.b_server_api

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.impl.settings.ServerSentEventSettingsImpl
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling
import akka.stream.scaladsl.Source
import akka.{NotUsed, actor => classic}
import com.github.nscala_time.time.Imports.DateTime

import scala.concurrent.duration.DurationInt

/**
 * akka-http 的服务端事件（Server-Send Event, SSE）支持
 *
 * SSE 是一种轻量化的标准协议，用于从 server 推送事件通知到 client。
 * 与 WebSocket 提供双工通信不同，SSE 只允许从 server 到 client 的单向通讯，
 * SSE 的优点在于简单，只依赖 HTTP，并提供对意外下线的 client 重试语义。
 *
 * 此外，如果对事件流有更加弹性的永久订阅诉求，可以使用 alpakka 提供的 EventSource 连接器。
 */

// SSE 服务，间隔 2 sec 发送当前时间
object SseServer {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(Behaviors.empty, "sse-server")
    implicit val executionContext = system.executionContext

    def route = {
      // EventStream 自动编码
      import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._

      path("events") {
        get {
          complete(
            Source
              .tick(2.seconds, 2.seconds, NotUsed)
              .map(_ => DateTime.now().toString("yyyy-MM-dd HH:mm:ss"))
              .map(time => ServerSentEvent(data= time, retry= 10 * 1000))
              .wireTap(e => println(s"Emits event: $e")) // 记录发送事件
              .keepAlive(1.seconds, () => ServerSentEvent.heartbeat)
          )
        }
      }
    }

    Http().newServerAt("127.0.0.1", 8080).bind(route)
  }

}


// SSE 客户端，监视 SSE api
// 使用 unType actor system： 由于 ServerSentEvent 相关的 unmarshalling api 还没有完全对 typed akka 兼容
object SseClient {

  //noinspection DuplicatedCode
  def main(args: Array[String]): Unit = {

    implicit val system = classic.ActorSystem("sse-client")
    implicit val exec = system.dispatcher

    import akka.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling._
    // EventStream 自动解码
    Http()
      .singleRequest(Get("http://127.0.0.1:8080/events"))
      .flatMap(Unmarshal(_).to[Source[ServerSentEvent, NotUsed]])
      .foreach(_.runForeach {
        case ServerSentEvent(data, _, _, _) =>
          println(s"received data from event: $data")
      })
  }
}


// SSE 客户端，监视 SSE api
// 使用 typed akka 强行兼容
object SseClientUseTypeActor {

  //noinspection DuplicatedCode
  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem(Behaviors.empty, "sse-server")
    implicit val executionContext = system.executionContext
    implicit val unmarshaller = EventStreamUnmarshalling.fromEventsStream(ServerSentEventSettingsImpl(system.settings.config))

    // EventStream 自动解码
    Http()
      .singleRequest(Get("http://127.0.0.1:8080/events"))
      .flatMap(Unmarshal(_).to[Source[ServerSentEvent, NotUsed]])
      .foreach(source => source.runForeach {
          case ServerSentEvent(data, _, _, _) => println(s"received data from event: $data")
        }
      )
  }
}

