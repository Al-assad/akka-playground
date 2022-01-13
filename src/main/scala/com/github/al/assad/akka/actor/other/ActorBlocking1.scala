package com.github.al.assad.akka.actor.other

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}

/**
 * Actor 阻塞测试
 * 每次 Bot 仅在启动阶段派生一个 BusyWorker， 该 BusyWorker 顺序 Assign 消息
 */
//noinspection ScalaFileName, DuplicatedCode
object BlockingActors {

  object Bot {
    case class Touch()
    case class Assign(seq: Long)

    def apply(): Behavior[Touch] = {
      var seqCount = 0L

      Behaviors.setup { ctx =>
        // 仅仅在启动阶段派发一个 worker
        val worker = ctx.spawn(BusyWorker(), "worker")
        Behaviors.receiveMessage { _ =>
          seqCount += 1
          ctx.log.info(s"Bot assign work: seq=${seqCount}")
          worker ! Assign(seqCount)
          Behaviors.same
        }
      }
    }
  }

  object BusyWorker {
    val rand = new scala.util.Random()

    def apply(): Behavior[Bot.Assign] = Behaviors.receive { (ctx, msg) =>
      val sleepMs = 2000 + rand.nextInt(2000)
      Thread.sleep(sleepMs)
      ctx.log.info(s"Worker finished: ${msg.seq}, cost ${sleepMs} millis.")
      Behaviors.same
    }
  }

  def main(args: Array[String]): Unit = {
    val bot = ActorSystem(Bot(), "bot")
    (1 to 10).foreach(_ => bot ! Bot.Touch())
  }
}


