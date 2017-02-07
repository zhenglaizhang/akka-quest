package net.zhenglai.akka.quest.basic.actordsl

import akka.actor.ActorDSL._
import akka.actor.ActorSystem


object ActorDSLMain extends App {

  implicit val system: ActorSystem = ActorSystem()

  val a = actor(new Act {
    become {
      case "hello" => sender() ! "hi"
    }
  })

  // TODO: to finish it
}
