package com.github.al.assad.akka.actor.other

import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy}

/**
 * Actor 阻塞测试
 * 使用 Router 改写 E_ActorBlockingTest
 */
//noinspection ScalaFileName, DuplicatedCode
object RouterBlockActors {

  object Bot {
    case class Touch()
    case class Assign(seq: Long)

    var seqCount = 0L

    def apply(): Behavior[Touch] =

      Behaviors.setup { ctx =>
        // Worker Actor Pool，尺寸为 5
        val workerPool = Routers.pool(poolSize = 5) {
          Behaviors.supervise(BusyWorker()).onFailure[Exception](SupervisorStrategy.restart)
        }
        // 为 Worker Actor 创建 Router
        val workerRouter = ctx.spawn(workerPool, "worker-pool")
        Behaviors.receive[Touch] { (ctx, _) =>
          seqCount += 1
          ctx.log.info(s"Bot assign work: seq=${seqCount}")
          // 向 Worker 路由派发任务
          workerRouter ! Assign(seqCount)
          Behaviors.same
        }
      }

  }

  object BusyWorker {
    val rand = new scala.util.Random()

    def apply(): Behavior[Bot.Assign] =
      Behaviors.receive { (ctx, msg) =>
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


