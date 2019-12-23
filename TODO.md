# TODO list

## Clustering

Clustering is not supporter yet.
It is closely related to the failure
strategies so check this out first. 

### **Roles**

App module should start different module
based on the role given to the cluster.

## Failure strategies

Each subsection in this section covers
one module and its possible problems with
failures on other modules.

_____________________________
### **Collector**
_____________________________
#### Problem

Collector can have problem if the Joiner
is malfunctioning.
If we know that the joiner left the cluster 
we should cache the data until joiner starts 
again so we can send him more data.

#### Solution

Having guardian actor for the collector listening to
the joiners in the cluster. If the joiner leaves
we should stop the collector and spawn another
actor `CachingCollector`. Caching collector
just saves the data in the ram.
If the `Joiner` actor appears the the guardian should
start the collector again and the caching collector
should send the joiner all of its cached data
and then stop.

#### Problem 

Another problem is related to external sources.
Id the external source (the web) is not responding
we should have timeouts and number of retries
after which we start sending `Failure` messages
so we can communicate it to the client.
If the external source comes back collector should resume.

#### Solution

As mentioned in the problem section. Timeouts and retries
on download after which a `Failure` message is send
for each retry. If at some point the request goes through
we just resume as normal 

_____________________________
### **Joiner**
_____________________________

#### Problem

No data from the collector

#### Solution

Timeouts for the collector messages. 
The same as with the recorder

#### Problem 

Recorder stops working

#### Solution

???

#### Problem

Warehouse stops working

#### Solution

???

_____________________________
### **Warehouse**
_____________________________

#### Problem

Joiner can malfunction and the order will be send to nothing.

#### Solution

Allow order to be lost? 
If the Joiner comes back it should resend its orders
(if it can somehow recover them)

_____________________________
### **Recorder**
_____________________________

#### Problem

If joiner malfunctions and the recorder won't receive
any messages the failure should be communicated to
the client

#### Solution

Timer which is reset each time a message is received.
If the timer timeouts we communicate the failure to the
client and reset the timer (or just wait for any message to
come and then reset).