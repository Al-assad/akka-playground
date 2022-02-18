package com.github.al.assad.akka

import akka.actor.typed.scaladsl.ActorContext
import org.slf4j.Logger

object BehaviorLoggerImplicit {
  def log(implicit ctx: ActorContext[_]): Logger = ctx.log
}


trait BehaviorLogger {
  def log(implicit ctx: ActorContext[_]): Logger = ctx.log
}
