#ifndef __GET_OPT_H__
#define __GET_OPT_H__

#include <string>
#include <sstream>
#include <vector>
#include <set>
#include <map>
#include "actor.h"

typedef std::map<std::string, std::vector<std::string> > Tokens;
typedef std::map<std::string, std::vector<std::string> >::const_iterator TokensIterator;

extern std::string input_file;
extern std::string write_file;
extern std::string config_file;
extern std::string trace_actor_name;
extern int nbLoops;
// Number of frames to display before closing application
extern int nbFrames;

template<typename T>
inline void convert(const std::string& s, T& res) {
	std::stringstream ss(s);
	ss >> res;
	if (ss.fail() || !ss.eof()) {
	}
}
template<typename T>
inline void convert(const std::vector<std::string>& s, std::set<T>& res) {

	for (auto p : s) {
		std::stringstream ss(p);
		T value;
		ss >> value;
		res.insert(value);
		if (ss.fail() || !ss.eof()) {
		}
	}

}

/* specialization for bool */
template<>
inline void convert(const std::string& s, bool& res) {
	if (s == "true")
		res = true;
	else if (s == "false")
		res = false;
	else {
	}
}

/* specialization for std::string */
template<>
inline void convert(const std::string& s, std::string& res) {
	res = s;
}

template<typename T> class Options;

class GetOpt {
public:
	GetOpt(int argc, char* argv[]);
	~GetOpt();

	void parse(int argc, char* argv[]);

	void get_help();

	void setActors(std::vector<Actor*> actors) {
		this->actors = actors;
	}
	;

	void setName(std::string name) {
		this->name = name;
	}
	;

	template<typename T> bool getOptionAs(const std::string&, T&);

	template<typename T> bool getOptionAs(const std::string&);

	template<typename T> bool getOptionAs(const std::string& s,
			std::set<T>& res);

	const Tokens& getTokens() const {
		return tokens;
	};

	std::set<std::string> get_trace_instance_name(){
		std::set<std::string> trace_instance_names;
		for(Actor* actor : actors){
			trace_instance_names.insert(actor->name());
		}

		return trace_instance_names;
	};


	void getOptions();
private:
	std::string name;

	Tokens tokens;

	std::vector<Actor*> actors;

	std::set<std::string> trace_instance_names;

	Actor* findActor(std::string name){
		for(Actor* actor : actors){
			if(actor->name().compare(name)==0){
				return actor;
			}
		}
		return NULL;
	}
};

template<typename T>
bool GetOpt::getOptionAs(const std::string& s, T& res) {
	return Options<T>(this)(s, res);
}

template<typename T>
bool GetOpt::getOptionAs(const std::string& s, std::set<T>& res) {
	return Options<T>(this)(s, res);
}

template<typename T>
bool GetOpt::getOptionAs(const std::string& s) {
	return Options<T>(this)(s);
}

template<typename T>
class Options {
public:
	Options<T>(const GetOpt* options) :
			options(options) {
	}

	bool operator ()(const std::string& s, T& res) {
		TokensIterator it = options->getTokens().find(s);
		if (it != options->getTokens().end()) {
			convert<T>((it->second)[0], res);
			return true;
		} else {
			return false;
		}
	}

	bool operator ()(const std::string& s) {
		TokensIterator it = options->getTokens().find(s);
		if (it != options->getTokens().end()) {
			return true;
		} else {
			return false;
		}
	}

	bool operator ()(const std::string& s, std::set<T>& res) {
		TokensIterator it = options->getTokens().find(s);
		if (it != options->getTokens().end()) {
			convert<T>((it->second), res);
			return true;
		} else {
			return false;
		}
	}

private:
	const GetOpt* options;
};

template<typename T>
class Options<std::vector<T> > {
public:
	Options<std::vector<T> >(const GetOpt* options) :
			options(options) {
	}

	void operator ()(const std::string& s, std::vector<T>& res) {
		Tokens tokens = options->getTokens();
		TokensIterator it = tokens.find(s);
		if (it != tokens.end()) {
			std::vector<std::string>::const_iterator vec_it;
			for (vec_it = it->second.begin(); vec_it != it->second.end();
					vec_it++) {
				T item;
				convert<T>(*vec_it, item);
				res.push_back(item);
			}
		} else {
			// option not found
		}
	}
private:
	const GetOpt* options;

};

#endif
