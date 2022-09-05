#ifndef EXELIXI_MAPPING_PARSER_H_
#define EXELIXI_MAPPING_PARSER_H_

#include <string>
#include <map>
#include <vector>
#include <set>
#include "tinyxml2.h"

#include "actor.h"

using namespace tinyxml2;

class MappingParser {
public:
	MappingParser(std::string& filename, std::vector<Actor*> actors);

	virtual ~MappingParser();

	std::map<std::string, std::vector<Actor*> > getPartitions() {
		return partitions;
	}

	std::vector<Actor*> getActors(std::string partitionId){
		return partitions[partitionId];
	}

private:

	void parse();

	void parsePartitions(XMLElement* parent);

	std::vector<Actor*> parseInstances(std::string id, XMLElement* parent);

	std::string& filename;

	std::vector<Actor*> actors;

	std::vector<Actor*> unmapped_actors;

	std::map<std::string, std::vector<Actor*> > partitions;

	Actor* find_actor(std::string name){

		for(Actor* actor : actors){
			if(actor->name().compare(name) == 0){
				return actor;
			}
		}

		return NULL;
	}
};

#endif // EXELIXI_MAPPING_PARSER_H_

