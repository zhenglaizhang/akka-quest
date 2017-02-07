# Play with AKKA


* Examples through the official doc

* Web crawler

* Patterns


#### Akka Streams vs Akka Actor

* Akka Streams is all about back-pressure but actor messaging is fire-and-forget without any back-pressure. 
* Adding back-pressure to actor messaging must still be handled on the application level using techniques for message flow control, such as acknowledgments, work-pulling, throttling.
* 

### Reactive Streams
  * Streams are Ubiquitous
    * ingesting, transforming and emitting data
    * request & responses
    * Moving Bulk Data over the Network
    * bad connection quality - need for back-pressure
  * Problem: Getting `Data` across an `Asynchronous Boundary` without running `OutOfMemory`
  * Reactive Streams is an initiative to provide a standard for asynchronous stream processing with non-blocking back pressure on the JVM
  * Supply and Demand 
    * data items flow downstream
    * demand flows upstream
    * data items flows only when there is demand
      * recipient is in control of incoming data rate
      * data in flight in bounded by signaled demand
      Publisher  <--(demand)-- Subscriber
                 -- (data) -->
  * Dynamic Push-Pull
    * `push` behavior when consumer is faster
    * `pull` behavior when producer is faster
    * switches automatically between these
    * batching demand allows batching data
  * Back-Pressure is Contagious
    * C is slow
      * B must slow down
        * A must slow down
    `A -> B -> C`
  * Back-Pressure can be Propagated
    * TCP for example has it built in

> Reactive Streams
   * asynchronous non-blocking data flow
   * asynchronous non-blocking demand flow
   * minimal coordination and contention
   * message passing allows for distribution across applications, nodes, CPUs, threads, actors, ...
   * http://reactive-streams.org/ 
  
  
### AKKA
  * Kafka + Akka === BFF (best friend forever)
    * Akka is Arbitrary processing
    * Kafka is somewhat more than a message queue, but very focused on "the log"
      * A publish-subscribe messaging rethought as a distributed commit log
    * Spark shines with it's data-science focus
  * Reactive Kafka
    * consumer/producer are RS Subscriber/Publisher
    
    
### Streams <=> Actors inter-op
  * Source.actorRef & Sink.actorRef (no back-pressure)
  * Source.queue & Sink.actorRefWithAct (safe)

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
  * Why you shouldn't use Futures for concurrency: good luck controlling that.
  * Using futures for concurrency in a cloud environment (read: anemic) where you can't predict user load is a good way to crash.
    * Now do 1000 of those at once. All of them returning an unknown amount of data and all of them processing at once. You will crash. Hello, OutOfMemoryError! Or, even more fun, Linux's memory killer just terminates you and you wind up with no error in your logs.
    * Don't use futures for concurrency. Use actors and pull work. Drop your requests into a queue and let actors ask for work as they're ready. Now, controlling concurrency is as simple as setting the size of the actor router in your config file!
    * I understand it to be recommended not to keep your Actors busy for too long.
    * Rather, create a Future in a closure in 'def receive' that does the work and sends a message when complete.
    * The entire idea is to keep intensive work predictable and under control. If the concern is blocking on the default dispatcher, move your worker actors to their own. Futures, BTW, have the same problem.
    * Under this pattern, it's perfectly okay for the worker actor to block because when pulling work, you won't be sending more messages to your worker anyways. The coordinator will keep track of which workers are busy and the worker won't receive any messages until it tells the coordinator that it's ready for more work.
    * If you have an Actor that can handle other messages, though, you might want to consider whether or not its a good idea to let its message queue build up. Futures don't have a message queue, so it's explicit.
    * That's why you typically have a coordinator actor in front of your worker actors which handles those messages. Essentially you want to avoid using the message queue of your workers if at all possible.
    * I think as long as the actor doesn't accept another work unit before the current one is complete, it would make much more sense to do break down actor tasks and run them asynchronously using futures but not send the response until it's complete.
    * if you want to control database usage, the connection pool is already a great place to do that. Or use actor pools to fix the less mature db connection pool
    * If you use futures for concurrency, you run the risk of trying to do more work than your system can handle. You probably won't notice this locally but go into production on an EC2 m1.medium and you will run in to problems.
    * If you use actors and pull work you can contain the amount of work done at once.
  * Configuring the thread pool limits the number of concurrent requests you will run, same as using a collection of actors. In both cases future requests to be run will be stored as objects - in one case a message in the queue, in the other case as a future waiting to run.
    * If you fetch asynchronously with actors you run the risk of having N x 30mb objects in your mailbox, same problem as if you asynchronously pulled N x 30mb with futures.
    * Actors are an amazing abstraction. But when utilized extensively even for the most basic things, their lack of type-safety and relative verbosity can lead to difficult to follow code.
    *  I would rather use Akka actors as dumb serial execution worker units than spawn futures and overload them. The only difficult thing with this approach would be to decide the actor pool size to ensure you don't overload yourself or are idle most of the time.
    * 

#### Stream

* A stream of information is like a river. Does the river care who observes it or who drinks from it? No, it doesn't.

#### Actor Lifecycle

![](http://doc.akka.io/docs/akka/2.4/_images/actor_lifecycle1.png)
