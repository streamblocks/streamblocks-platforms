#ifndef __ACTOR_H__
#define __ACTOR_H__


class Actor {

public:
    virtual bool run() = 0;

    virtual ~Actor(){}

};

#endif //__ACTOR_H__