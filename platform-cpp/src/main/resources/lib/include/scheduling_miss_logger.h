#ifndef __EXELIXI_SW_SCHEDULING_MISS_LOGGER_H__
#define __EXELIXI_SW_SCHEDULING_MISS_LOGGER_H__

#include <stdlib.h>
#include <inttypes.h>
#include <stdint.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <vector>
#include <string>
#include <map>
#include "tinyxml2.h"

using namespace std;
using namespace tinyxml2;

class SchedulingMissLogger {
public:

	SchedulingMissLogger(string networkName) {
		this->networkName = networkName;
	}

	void addSchedulingMiss(string actor, long value) {
		missMap[actor] = value;
	}

	void write() {
		// create XML file
		FILE* file = fopen("scheduling_miss.xml", "w");
		XMLPrinter sprinter(file);
		sprinter.OpenElement("scheduling-miss");
		sprinter.PushAttribute("network", networkName.c_str());

		char buffer[256];
		for (auto const &ent1 : missMap) {
			sprinter.OpenElement("actor");
			sprinter.PushAttribute("id", ent1.first.c_str());
			sprintf(buffer, "%ld", ent1.second);
			sprinter.PushAttribute("miss", buffer);
			sprinter.CloseElement();
		}

		sprinter.CloseElement();
		fclose(file);
	}

private:

	string networkName;
	map<string, long> missMap;
};

#endif // __EXELIXI_SCHEDULING_MISS_LOGGER_H__

