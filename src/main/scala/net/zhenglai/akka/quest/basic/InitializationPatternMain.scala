package net.zhenglai.akka.quest.basic

import akka.actor.{ Actor, ActorLogging }


// During the lifetime of an ActorRef, an actor can potentially go through several restarts, where the old instance is replaced by a fresh one, invisibly to the outside observer who only sees the ActorRef.
//  1. Initialization via constructor
//    The constructor is invoked for every incarnation of the actor, therefore the internals of the actor can always assume that proper initialization happened.
//    This is also the drawback of this approach, as there are cases when one would like to avoid reinitializing internals on restart. For example, it is often useful to preserve child actors across restarts.
//  2. via preRestart
//    The method preStart() of an actor is only called once directly during the initialization of the first instance, that is, at creation of its ActorRef.
//    In the case of restarts, preStart() is called from postRestart(), therefore if not overridden, preStart() is called on every incarnation. 
//    However, by overriding postRestart() one can disable this behavior, and ensure that there is only one call to preStart().

class ParentActor extends Actor with ActorLogging {

  @scala.throws[Exception](classOf[Exception])
  override def preStart() = {
    // initialize childern actors
  }

  // Overriding postRestart to disable the call to preStart()
  // after restarts
  override def postRestart(reason: Throwable): Unit = ()

  // The default implementation of preRestart() stops all the children
  // of the actor. To opt-out from stopping the children, we
  // have to override preRestart()
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    // Keep the call to postStop(), but no stopping of children
    postStop()
  }

  // todo
  // Please note, that the child actors are still restarted,
  // but no new ActorRef is created. One can recursively apply the same principles for the children, ensuring that their preStart() method is called only at the creation of their refs.

  override def receive = ???
}

object InitializationPatternMain extends App {

}
