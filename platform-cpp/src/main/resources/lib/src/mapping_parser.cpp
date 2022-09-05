#include "mapping_parser.h"
#include <iostream>
#include <algorithm>

MappingParser::MappingParser(std::string& filename,
		std::vector<Actor*> actors) :
		filename(filename), actors(actors), unmapped_actors(actors) {
	if (filename.empty()) {
		std::vector<Actor*> singlePartition;

		std::map<std::string, Actor*>::iterator ait;

		partitions["main"] = actors;
	} else {
		parse();
	}
}

MappingParser::~MappingParser() {
}

void MappingParser::parse() {
	XMLDocument doc;
	doc.LoadFile(filename.c_str());
	XMLElement* elt =  doc.FirstChildElement("Configuration" )->FirstChildElement("Partitioning");
	parsePartitions(elt);

	if(!unmapped_actors.empty()){
		std::cout<<"[Partition Parser ERROR] there are "<< unmapped_actors.size() << " unmapped actors!\n";
		for(Actor* actor : unmapped_actors){
			std::cout<<"  [unmapped] ->" << actor->name()<<"\n";
		}
	}
}

void MappingParser::parsePartitions(XMLElement* parent) {
	for(XMLElement* child = parent->FirstChildElement("Partition"); child != NULL; child = child->NextSiblingElement("Partition"))
	{
		std::string id = std::string(child->Attribute("id"));
		partitions[id] = parseInstances(id, child);
	}
}

std::vector<Actor*> MappingParser::parseInstances(std::string partitionId, XMLElement* parent) {
	std::vector<Actor*> instances;
	for(XMLElement* child = parent->FirstChildElement("Instance"); child != NULL; child = child->NextSiblingElement("Instance"))
	{
		XMLElement* elt = child->ToElement();
		std::string instId = std::string(elt->Attribute("id"));
		Actor* actor = find_actor(instId);
		if(actor!=NULL){
			instances.push_back(actor);
			unmapped_actors.erase(std::remove(unmapped_actors.begin(), unmapped_actors.end(), actor), unmapped_actors.end());
		}else{
			std::cout<<"[Partition Parser ERROR] (partition="<<partitionId<<") instance="<<instId<<" not found in the generated network!\n";
		}
	}

	return instances;
}

