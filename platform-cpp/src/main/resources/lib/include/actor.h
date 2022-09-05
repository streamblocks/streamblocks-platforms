#ifndef __EXELIXI_ACTOR_H__
#define __EXELIXI_ACTOR_H__
#include <string>

enum EStatus {
	None, hasSuspended, hasExecuted, Starvation, Fullness
};

class Actor {

public:
	//virtual void initialize() = 0;
	

	virtual bool action_selection(EStatus&) = 0;
	
	bool has_executed(){ return executed_once; }
	
	virtual std::string name() {return "<unknown>"; }
	
	
	virtual ~Actor(){
	}
	
protected:
    bool executed_once = false;

};

#endif // __EXELIXI_ACTOR_H__
