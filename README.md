##Outline

This is a simple wrapper around a standard HashMap to support configurable expiry. 

Keys can be written with an associated timeout. 

Eviction is automatically scheduled using a background thread. 


###Notes on the Implementation
As data is written to the ExpiryMap, entries recording the expiry time are placed on a PriorityQueue. The queue is ordered 
by the entry's expiry time so items expiring soonest move to the head of the queue. 
