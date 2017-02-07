package net.zhenglai.akka.quest.basic.dispatchers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object DispatcherMain extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()

  // for use with Futures, Scheduler, etc.
  implicit val ec = system.dispatchers.lookup("my-dispatcher") // default dispatch if not configured otherwise


  Thread.sleep(1000)
  system.terminate()
}
