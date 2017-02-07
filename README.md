# Play with AKKA


* Examples through the official doc

* Web crawler

* Patterns


* Mats

  * Don't use actors for concurrency. Instead, use actors for state and use futures for concurrency.
  https://www.chrisstucchio.com/blog/2013/actors_vs_futures.html
  * Actors are an effective tool for two purposes, 
    * maintaining state and
    * providing a messaging endpoint. 
    * In all other circumstances, it is probably better to use Futures
  * An actor, has the single-threaded nature, while future hasn't
  * Another way to gain concurrency using actors would be to spin up multiple actors and put them behind a router. This will give a certain degree of concurrency, but is unnecessarily complicated.
    * It also violates separation of concerns. Fundamentally, handling concurrency is the role of the ActorSystem. 
    *  If we use Actors to manage concurrency, there are two places this must be configured 
      - in the ActorSystem itself, and 
      - in the Router which routes requests to the actors. 
    * you shouldn't use your ActorSystem's thread pool to manage the number of concurrent database queries either. Your ActorSystem should manage your Thread resources - your database connection pool should actually be what is used to manage database concurrency. It's highly unlikely that your home grown concurrency system will be faster than BoneCP.



#### Actor Lifecycle

![](http://doc.akka.io/docs/akka/2.4/_images/actor_lifecycle1.png)
