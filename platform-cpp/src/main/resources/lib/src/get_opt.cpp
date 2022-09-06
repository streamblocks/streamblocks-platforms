#include "get_opt.h"
#include <iostream>
#include <stdlib.h>

namespace sb {
    namespace options {
        std::string config_file;
		std::string input_file;
		std::string write_file;

		bool disable_display = false;
		int nbLoops = -1;
		int nbFrames = -1;
    }
}

GetOpt::GetOpt(int argc, char* argv[]) {
	parse(argc, argv);
}

GetOpt::~GetOpt() {
}

void GetOpt::parse(int argc, char* argv[]) {
	std::vector<std::string> currOptionValues;
	std::string optionName;
	for (int i = 1; i < argc; i++) {
		if (argv[i][0] == '-') {
			optionName = &argv[i][1];
			tokens[optionName] = currOptionValues;
		} else {
			tokens[optionName].push_back(&argv[i][0]);
		}
	}
}

void GetOpt::get_help() {
	std::cout << "Exelixi CPP" << std::endl;

	std::cout << std::endl;

	std::cout << "Usage: " << "./" << this->name << " [options]" << std::endl;

	std::cout << std::endl;

	std::cout << "Common arguments:" << std::endl;

	std::cout << "-m <file>"
			<< " Define a predefined actor mapping on multi-core platforms using the given XML file."
			<< std::endl;

	std::cout << "-h" << " Print this message." << std::endl;

	std::cout << std::endl;

	std::cout << "Specific arguments:" << std::endl;

	std::cout << "-i <file>" << " Specify an input file." << std::endl;

	std::cout << "-l <number>"
			<< " Set the number of readings of the input file before exiting."
			<< std::endl;

	std::cout << "-w <file>" << " Specify a file to write the output stream."
			<< std::endl;

	std::cout << "-t <instance name(s)>"
			<< " Specify the actor instances to generate the fifo traces."
			<< std::endl;

}

void GetOpt::getOptions() {
	std::string help;
	bool is_help = this->getOptionAs<std::string>("h");
	if (is_help) {
		get_help();
		exit(EXIT_SUCCESS);
	}
	this->getOptionAs<std::string>("i", sb::options::input_file);
	this->getOptionAs<std::string>("m", sb::options::config_file);
	this->getOptionAs<std::string>("w", sb::options::write_file);
	bool is_trace = this->getOptionAs<std::string>("t", this->trace_instance_names);
	if (is_trace) {
		if (!trace_instance_names.empty()) {
			for (auto instance_name : trace_instance_names) {
				Actor* search = findActor(instance_name);
				if (search == NULL) {
					std::cout << "Instance: " << instance_name
							<< " does not exist" << std::endl;
					exit(EXIT_FAILURE);
				}
			}
		} else {
			std::cout << "Info: All traces will be generated, it might take more time!" << std::endl;
			for(Actor* actor : actors){
				trace_instance_names.insert(actor->name());
			}
		}
	}
	bool exists = this->getOptionAs<int>("l", sb::options::nbLoops);
	if (!exists)
		sb::options::nbLoops = -1;
	exists = this->getOptionAs<int>("f", sb::options::nbFrames);
	if (!exists)
		sb::options::nbFrames = -1;
}
