package com.github.al.assad.akka.actor.other

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, Terminated}

/**
 * Actor 阻塞测试
 * Bot 每接收一个消息时派生一个 BusyWorker， 该 BusyWorker 顺序 Assign 消息
 */
//noinspection ScalaFileName, DuplicatedCode
object AnotherBlockingActors {

  object Bot {
    case class Touch()
    case class Assign(seq: Long)

    def apply(): Behavior[Touch] =
      Behaviors.setup { ctx =>
        var seqCount = 0L
        Behaviors
          .receive[Touch] { (context, msg) =>
            // 每次接收信息时派发一个 Worker Actor
            seqCount += 1
            val worker = ctx.spawn(BusyWorker(), s"worker-$seqCount")
            context.watch(worker)
            ctx.log.info(s"Bot assign work: seq=${seqCount}")
            worker ! Assign(seqCount)
            Behaviors.same
          }
          .receiveSignal {
            case (context, Terminated(worker)) =>
              context.log.info(s"$worker is terminated")
              Behaviors.same
          }
      }

  }

  object BusyWorker {
    val rand = new scala.util.Random()

    def apply(): Behavior[Bot.Assign] = Behaviors.receive { (ctx, msg) =>
      val sleepMs = 2000 + rand.nextInt(2000)
      Thread.sleep(sleepMs)
      ctx.log.info(s"Worker finished: ${msg.seq}, cost ${sleepMs} millis.")
      // 处理结束后自动销毁
      Behaviors.stopped
    }
  }

  def main(args: Array[String]): Unit = {
    val bot = ActorSystem(Bot(), "bot")
    (1 to 10).foreach(_ => bot ! Bot.Touch())
  }

}


