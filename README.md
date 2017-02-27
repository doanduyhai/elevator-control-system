#Elevator Control System

##What is is ?

This project is a simple demo of Akka actors to control an elevator system

## How to use ?

You need to have `Maven 3.x`, `Java 8` and `Git` installed & working

- first checkout the project with `git clone https://github.com/doanduyhai/elevator-control-system`
- then switch to the folder `elevator-control-system`
- package the project with `mvn clean package`. There is an `akka-elevator-control-system-<version>.jar` file
  generated in the `target` folder
- to run the demo, type `java -jar target/akka-elevator-control-system-<version>.jar <scenario-file>` 
where `<scenario-file> is a scenario file. You can find some examples of these in the folder `sample_scenarios`
  
  
##Scenario file format
  
Below is an example of a scenario file:
  
```
timeStep 1s
1[1->3]:Move(4,0)
2: Move(1,2)
3: AtFloor(3)
4[6->2]: AtFloor(5)
OrderQueue: Move(5,8),Move(1,3),Move(2,3)
MaxOrderQueueSize: 10
Pickup(0,4): 2ms
Pickup(6,4): 5s
Pickup(1,10): 15s
Pickup(0,2): 25s
Pickup(5,1): 30s
Pickup(0,7): 40s
```  

The format of the scenario file is:

- `timeStep`: specify the time delay between each status evaluation. 
            Format = `timeStep: <duration><time-unit>`. 
            `<duration>` should be a positive integer. 
            The supported `<time-unit>` are second (`s`) or millisecond (`ms`). 
            Ex: `timeStep: 20ms`

- `Elevator`: specify each elevator status.
            Format = `<elevator-number>([<scheduledOrder>])?: <current-status>`. 
            The `<scheduledOrder>` format is `[from]->[to]` where `[from]` is the source floor and `[to]` the destination floor. 
            The `<current-status>` format can be: `Move(<from>, <to>)` or `AtFloor(<floor>)`. 
            Ex:
                - `1: Move(0,2)` elevator 1 with no scheduled order moving from 0 to 2
                - `2[4->2]: Move(3,5)` elevator 2 with a scheduled order Move(4,2) moving from 3 to 5
                - `3: AtFloor(0)` elevator 3 with no scheduled and idle at floor 0
                - `4[0-3]: AtFloor(5)` elevator 4 with a scheduled order Move(0,3) currently at floor 5

- `OrderQueue`: specify a list of orders to be enqueued in the control system. 
            Format = `OrderQueue: <move>,<move>, ...,<move>`. 
            `<move> format is simply Move(<from>,<to>)`. 
            Ex:
                - `OrderQueue: Move(0,4)`
                - `OrderQueue: Move(1,3), Move(7,9), Move(1,2)`


- `MaxOrderQueueSize`: specify the max size of the order queue. 
            Ex:
                - `MaxOrderQueueSize: 3`

- `scheduled pickup`: specify a sequence of pickup ordered, delayed with a given time unit. 
            Format = `Pickup(<from>,<to>): <duration><time-unit>`. 
            `<from>` is the source floor. 
            `<to>` is the target floor. 
            `<duration>` is a positive integer. 
            `<time-unit>` are second (`s`) or millisecond (`ms`). 
            Ex:
                - `Pickup(0,6): 3s`  schedule a pickup from 0 to 6 in 3 seconds
                - `Pickup(5,4): 30ms`  schedule a pickup from 5 to 4 in 30 milliseconds

There are some sample scenarios you can play with located in folder `sample_scenarios`

## Design

Designing an elevator control system is inherently a distributed system problem. For those kind of scenarios, there are 2 approaches:

- the multi-threaded design approach
- the message-passing approach with actor system
 
Unless being a very seasoned expert in multi-threading, the 2<sup>nd</sup> approach is much more simpler. The idea is to model each element of the system
as a finite state machine which exchanges messages between them to move forward. `Akka` is right now the de-facto standard actor system in the JVM eco-system
so our implementation will use it
 
### The actors

The system is composed of 2 types of actors:

- the **ElevatorActor** which represents the behavior of a single elevator. There can be 1 to N elevators in the system
- the **ControlSystemActor** which is a singleton (in the sense that there will be at most only one single actor in the system). This actor is responsible to dispatch
order to the elevators based on some rules, collect their state and display them on the console
 
To avoid circular dependency, each **ElevatorActor** keeps a reference on the **ControlSystemActor** but the latter will only discover the elevator actors at runtime
   
### ElevatorActor
   
This actor is responsible to control the behavior of a single elevator. It is stateful and contains the following properties injected at creation time:

- `id`: the id of the elevator
- `controlSystem`: a reference to the **ControlSystemActor** singleton
- `movingSpeed`: each of the elevator can have different moving speed, although for the sake of simplicity they are fixed to the same value for all elevator in the current implementation
- `status`: the current status of the elevator, can be either `Move(from,to)` or `AtFloor(currentFloor)` 
- `scheduledOrder`: optionally, each elevator can keep 1 order in memory, to be processed once it will be available again (e.g. its states == `AtFloor()` in the future

Each elevator accepts the following types of message:

- `StartSimulation`: begin the current simulation. Send its current status to the `controlSystem` and schedule the next status base on its current status (`AtFloor` or `Move`)
- `Pickup(from,to)`: receive a pickup order. 2 outcomes are possible
     - if the current status = `AtFloor`, accept the pickup order and schedule the next status
     - if the current status = `Move(_,_)` and there is no scheduled order, schedule it
     - if the current status = `Move(_,_)` and there is already a scheduled order, reject the pickup. This case cannot happen theoretically because the `controlSystem` is responsible for pickup order dispatch and will not send the order to an elevator which is moving and have already a scheduled order
   After deciding of the outcome, the current status will be sent to the `controlSystem`
   If there is no ongoing simulation, schedule the next status  
- `EnRoute`: the simulation is onging. 
    - if the elevator is moving, schedule the next status
    - if the elevator is `AtFloor(currentFloor)`
        - if there is no scheduled order, do thing
        - else pickup the scheduled order and schedule the next status
          Depending on the value of `currentFloor`, the elevator may have to position itself to the `order.fromFloor` first before being able to execute the order. When moving to the `order.fromFloor`, the scheduled order is still scheduled, to avoid that `controlSystem` assigns another order to the current elevator            

There is a scheduling mechanism to keep the elevator moving. It is based on `context.system.scheduler.scheduleOnce(movingSpeed, self, EnRoute(nextStep))`

To compute the next status based on the current status, the trait `ElevatorStatus` (whose impl are `Move(_,_)` and `AtFloor(_)`) defines the method `nextStep`

- if current status is `Move(from,to)`
    - if `|from - to| == 1`, next status = `AtFloor(to)`
    - elseif `to > from`, next status = `Move(from + 1, to)`
    - elseif 'from == to`, next status = `AtFloor(to)`
    - else next status = `Move(from - 1, to)`
- if current status is `AtFloor(floor)`, next status = `AtFloor(floor)`     

### ControlSystemActor
    
This actor is fairly simple. It has the following states:

- `expectedElevatorCount`: number of elevators the system is supposed to manage
- `orderQueue`: optional queue or pickup orders
- `maxQueueSize`: self-explanatory
- `printStream`: which defaults to `System.out`. One can redirect the printStream to a `ByteArrayOutputStream` for testing purpose

The actor accepts the following message types:

- `UpdateStatus(elevatorId, status, scheduledOrder)`: update the state of elevator given its id. The `controlSystem` keeps all elevator statuses in a `Map[elevatorId, (status, scheduledOrder)]`. This `elevatorStatusMap` is empty initially at start-up time
  When receiving an `UpdateStatus`, the `elevatorStatusMap` is updated.
  The `controlSystem` only start processing pickup orders and printing elevator statuses only when the `elevatorStatusMap` size is >= the `expectedElevatorCount`, otherwise nothing is done because not all elevators has send its status to the `controlSystem`
  If all elevators are **registered**
    - print the current order queue 
    - print the `elevatorStatusMap`
    - if the order queue is not empty
        - pick an `availableElevator` (see definition below), 
            - dequeue an order 
            - push the dequeued order to the selected elevator
            - **pro-actively** update the `elevatorStatusMap`. Indeed after that the selected elevator receives the sent order, it will send an `UpdateStatus` to the `controlSystem` and the `elevatorStatusMap` will be updated. However this updates encurs a delay and in the mean time, another pickup order can arrive and there is a risk that the `controlSystem` dispatches the new order to the same selected elevator again. That's why we need to update the `elevatorStatusMap` right-away to shield ourself from such scenarios 

- `Pickup(from,to)`: 
    - if there is an `availableElevator` 
        - dispatch the order to the selected elevator
        - pro-actively update the `elevatorStatusMap`
    - else enqueue the pickup order                 
  
An `availableElevator` has

- either `AtFloor(_)` status **and no scheduled order**
    We exclude the case `AtFloor(_)` + scheduled order because the target elevator may be **en route** to pick its scheduled order so we should not assign it another order
- or `Move(_,_)` status **and no scheduled order**


## Display format

The display of the `elevatorStatusMap` follows some convention. Each line represents an elevator status.
The line starts with the elevator id, followed by optionally a scheduled order in square brackets `[from->to]`.
Then is displayed the elevator status.

- ` _ ` represents an intermediary floor
- `|3|` represents the status `AtFloor(3)`
- `|2>` represents a source floor in `Move(2,target)` where `target` > 2
- `<5|` represents a source floor in `Move(5,target)` where `target` < 5
- `{6}` represents a target floor in `Move(?,6)`

Example: 

- elevator 1, Move(2,4), no scheduled order: `1: _  _ |2> _ {4}`
- elevator 2, Move(0,3), schedule order Move(4,2): `2[4->2]:|0> _  _ {3}`
- elevator 2, Move(5,2), no scheduled order: `3: _  _ {2} _  _ <5|`

Examples of output display

```
--------------------------------------------------
1[    ]:  _  _  _ |3|
2[6->4]:  _  _  _  _ |4> _ {6}
3[0->4]: {0} _  _  _  _  _  _ <7|
4[    ]:  _  _ |2>{3}
--------------------------------------------------
```

```
--------------------------------------------------
1[    ]:  _  _  _ |3|
2[    ]:  _  _  _  _ {4}<5|
3[0->4]: {0} _  _  _ <4|
4[    ]:  _  _  _ |3|
--------------------------------------------------
```

## The scenario parser

To build the scenario parser, the Scala combinator parser is used. There are much faster alternatives, like **[Parboiled2]** but the objective here is to have the parser working, performance not being the primary criteria

Most of the logic is defined in the class `ScenarioParser`. We define some regular expressions to parse each line of the scenario file by following the defined syntax



[Parboiled2]: https://github.com/sirthias/parboiled2