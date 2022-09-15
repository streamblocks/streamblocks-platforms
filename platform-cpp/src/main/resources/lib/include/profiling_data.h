#pragma once

#include "math.h"
#include "tinyxml2.h"
#include <inttypes.h>
#include <map>
#include <stdint.h>
#include <stdlib.h>
#include <string>
#include <sys/stat.h>
#include <sys/types.h>
#include <vector>

class ProfilingData {
public:
  ProfilingData(std::string networkName) { this->networkName = networkName; }

  void addFiring(std::string actor, std::string action, unsigned int cycles) {
    cyclesMap[actor][action].push_back(cycles);
  }

  void addScheduling(std::string actor, std::string lastAction,
                     std::string selectedAction, unsigned int cycles) {
    schedulerCyclesMap[actor][lastAction][selectedAction].push_back(cycles);
  }

  void generate_results(const char *path, bool storeFiringsCycles,
                        bool filter) {
    if (storeFiringsCycles) {
      std::string res_path = path;
      res_path.append("firings/");
      mkdir(res_path.c_str(), S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH);
      for (auto const &ent1 : cyclesMap) {
        std::string actor = ent1.first;
        for (auto const &ent2 : ent1.second) {
          std::string action = ent2.first;
          std::vector<unsigned int> cycles = ent2.second;
          std::string tpath = res_path;
          const char *fileName = tpath.append(actor)
                                     .append("-")
                                     .append(action)
                                     .append(".csv")
                                     .c_str();
          FILE *file = fopen(fileName, "w");
          for (int i = 0; i < cycles.size(); i++) {
            fprintf(file, "%u\n", cycles.at(i));
          }
          fclose(file);
        }
      }
    }

    // create XML file
    std::string tpath = path;
    FILE *file = fopen(tpath.append(networkName + ".sxdf").c_str(), "w");
    tinyxml2::XMLPrinter sprinter(file);
    sprinter.OpenElement("network");
    sprinter.PushAttribute("name", networkName.c_str());

    for (auto const &ent1 : schedulerCyclesMap) {
      // std::string actor = ent1.first;
      sprinter.OpenElement("actor");
      sprinter.PushAttribute("id", ent1.first.c_str());
      for (auto const &ent2 : ent1.second) {
        // std::string lastAction = ent2.first;
        for (auto const &ent3 : ent2.second) {
          // std::string action = ent3.first;

          sprinter.OpenElement("scheduling");
          sprinter.PushAttribute("source", ent2.first.c_str());
          sprinter.PushAttribute("target", ent3.first.c_str());

          std::vector<unsigned int> cycles = ent3.second;
          int size = cycles.size();

          bool use_filter = filter && size > 3;

          double mean = 0.0;
          double var = 0;
          unsigned long sum = 0;
          unsigned int min = 0;
          unsigned int max = 0;
          int discarded = 0;

          if (use_filter) {
            // get statistics
            statistics(cycles, &mean, &var);

            // filter the data
            // compute the mean
            double long _sum = 0;
            double _threshold = mean + 2 * sqrt(var);
            int _filtered_size = 0;

            min = UINT_MAX;
            max = 0;
            for (int i = 0; i < size; i++) {
              unsigned int v = cycles.at(i);
              if (v <= _threshold) {
                if (v > max) {
                  max = v;
                }
                if (v < min) {
                  min = v;
                }
                _sum += v;
                _filtered_size++;
              }
            }

            mean = _sum / (double)_filtered_size;
            discarded = size - _filtered_size;

            // now compute the variance
            if (_filtered_size > 1) {
              long double _sum2 = 0.0;
              for (int i = 0; i < size; i++) {
                unsigned int v = cycles.at(i);
                if (v <= _threshold) {
                  _sum2 += sqr(mean - v);
                }
              }
              var = _sum2 / (_filtered_size - 1);
            } else {
              var = 0;
              if (_filtered_size == 0) {
                min = max = 0;
                mean = 0.0;
              }
            }
          } else if (size > 0) {
            min = UINT_MAX;
            max = 0;
            sum = 0;
            for (int i = 0; i < size; i++) {
              unsigned int v = cycles.at(i);
              sum += v;
              if (v > max) {
                max = v;
              }
              if (v < min) {
                min = v;
              }
            }

            mean = sum / (double)size;

            // compute the variance
            if (size > 1) {
              long double _sum2 = 0.0;
              for (int i = 0; i < size; i++) {
                _sum2 += sqr(mean - cycles.at(i));
              }
              var = _sum2 / (size - 1);
            }
          }

          sprinter.PushAttribute("clockcycles", format("%f", mean).c_str());
          sprinter.PushAttribute("clockcycles-min", format("%u", min).c_str());
          sprinter.PushAttribute("clockcycles-max", format("%u", max).c_str());
          sprinter.PushAttribute("clockcycles-var", format("%f", var).c_str());

          sprinter.CloseElement();
        }
      }
      sprinter.CloseElement();
    }

    sprinter.CloseElement();
    fclose(file);

    // create XML file
    tpath = path;
    file = fopen(tpath.append(networkName + ".exdf").c_str(), "w");

    tinyxml2::XMLPrinter printer(file);
    printer.OpenElement("network");
    printer.PushAttribute("name", networkName.c_str());

    for (auto const &ent1 : cyclesMap) {
      printer.OpenElement("actor");
      printer.PushAttribute("id", ent1.first.c_str());
      for (auto const &ent2 : ent1.second) {
        printer.OpenElement("action");
        printer.PushAttribute("id", ent2.first.c_str());
        std::vector<unsigned int> cycles = ent2.second;
        int size = cycles.size();

        bool use_filter = filter && size > 3;

        double mean = 0.0;
        double var = 0;
        unsigned long sum = 0;
        unsigned int min = 0;
        unsigned int max = 0;
        int discarded = 0;

        if (use_filter) {
          // get statistics
          statistics(cycles, &mean, &var);

          // filter the data
          // compute the mean
          double long _sum = 0;
          double _threshold = mean + 2 * sqrt(var);
          int _filtered_size = 0;

          min = UINT_MAX;
          max = 0;
          for (int i = 0; i < size; i++) {
            unsigned int v = cycles.at(i);
            if (v <= _threshold) {
              if (v > max) {
                max = v;
              }
              if (v < min) {
                min = v;
              }
              _sum += v;
              _filtered_size++;
            }
          }

          mean = _sum / (double)_filtered_size;
          discarded = size - _filtered_size;

          // now compute the variance
          if (_filtered_size > 1) {
            long double _sum2 = 0.0;
            for (int i = 0; i < size; i++) {
              unsigned int v = cycles.at(i);
              if (v <= _threshold) {
                _sum2 += sqr(mean - v);
              }
            }
            var = _sum2 / (_filtered_size - 1);
          } else {
            var = 0;
            if (_filtered_size == 0) {
              min = max = 0;
              mean = 0.0;
            }
          }
        } else if (size > 0) {
          min = UINT_MAX;
          max = 0;
          sum = 0;
          for (int i = 0; i < size; i++) {
            unsigned int v = cycles.at(i);
            sum += v;
            if (v > max) {
              max = v;
            }
            if (v < min) {
              min = v;
            }
          }

          mean = sum / (double)size;

          // compute the variance
          if (size > 1) {
            long double _sum2 = 0.0;
            for (int i = 0; i < size; i++) {
              _sum2 += sqr(mean - cycles.at(i));
            }
            var = _sum2 / (size - 1);
          }
        }

        printer.PushAttribute("clockcycles", format("%f", mean).c_str());
        printer.PushAttribute("clockcycles-min", format("%u", min).c_str());
        printer.PushAttribute("clockcycles-max", format("%u", max).c_str());
        printer.PushAttribute("clockcycles-var", format("%f", var).c_str());
        // printer.PushAttribute("firings", format("%d", size).c_str());
        // printer.PushAttribute("discarded", format("%d", discarded).c_str());

        printer.CloseElement();
      }
      printer.CloseElement();
    }
    printer.CloseElement();
    fclose(file);
  }

private:
  std::string networkName;
  std::map<std::string, std::map<std::string, std::vector<unsigned int>>>
      cyclesMap;
  std::map<
      std::string,
      std::map<std::string, std::map<std::string, std::vector<unsigned int>>>>
      schedulerCyclesMap;

  template <typename... Ts>
  static std::string format(const std::string &fmt, Ts... vs) {
    char b;
    unsigned required = std::snprintf(&b, 0, fmt.c_str(), vs...) + 1;
    char bytes[required];
    std::snprintf(bytes, required, fmt.c_str(), vs...);

    return std::string(bytes);
  }

  inline static double sqr(double x) { return x * x; }

  int statistics(std::vector<unsigned int> y, double *mean, double *var) {
    long double _sum = 0.0;
    double _mean = 0.0;

    int n = y.size();

    if (n < 2) {
      return 1;
    }

    // average
    for (int i = 0; i < n; i++) {
      _sum += y.at(i);
    }
    *mean = _sum / (double)n;

    // variance
    long double _sum2 = 0.0;
    for (int i = 0; i < n; i++) {
      _sum2 += sqr(*mean - y.at(i));
    }

    *var = _sum2 / (n - 1);

    return 0;
  }
};