#pragma once
#include <cstdint>
#include <map>
#include <sstream>
#include <string>

class OpCounters {

public:
  uint32_t BINARY_BIT_AND;
  uint32_t BINARY_BIT_OR;
  uint32_t BINARY_BIT_XOR;
  uint32_t BINARY_DIV;
  uint32_t BINARY_DIV_INT;
  uint32_t BINARY_EQ;
  uint32_t BINARY_EXP;
  uint32_t BINARY_GT;
  uint32_t BINARY_GE;
  uint32_t BINARY_LT;
  uint32_t BINARY_LE;
  uint32_t BINARY_LOGIC_OR;
  uint32_t BINARY_LOGIC_AND;
  uint32_t BINARY_MINUS;
  uint32_t BINARY_PLUS;
  uint32_t BINARY_MOD;
  uint32_t BINARY_TIMES;
  uint32_t BINARY_NE;
  uint32_t BINARY_SHIFT_LEFT;
  uint32_t BINARY_SHIFT_RIGHT;
  uint32_t UNARY_BIT_NOT;
  uint32_t UNARY_LOGIC_NOT;
  uint32_t UNARY_MINUS;
  uint32_t UNARY_NUM_ELTS;
  uint32_t DATAHANDLING_STORE;
  uint32_t DATAHANDLING_ASSIGN;
  uint32_t DATAHANDLING_CALL;
  uint32_t DATAHANDLING_LOAD;
  uint32_t DATAHANDLING_LIST_LOAD;
  uint32_t DATAHANDLING_LIST_STORE;
  uint32_t FLOWCONTROL_IF;
  uint32_t FLOWCONTROL_WHILE;
  uint32_t FLOWCONTROL_CASE;

  std::map<std::string, int> reads;
  std::map<std::string, int> writes;

  OpCounters(std::string actor, std::string action, long long firingId,
             std::map<std::string, int> iPortRate,
             std::map<std::string, int> oPortRate)
      : actor(actor), action(action), firingId(firingId), iPortRate(iPortRate),
        oPortRate(oPortRate) {

    BINARY_BIT_AND = 0;
    BINARY_BIT_OR = 0;
    BINARY_BIT_XOR = 0;
    BINARY_DIV = 0;
    BINARY_DIV_INT = 0;
    BINARY_EQ = 0;
    BINARY_EXP = 0;
    BINARY_GT = 0;
    BINARY_GE = 0;
    BINARY_LT = 0;
    BINARY_LE = 0;
    BINARY_LOGIC_OR = 0;
    BINARY_LOGIC_AND = 0;
    BINARY_MINUS = 0;
    BINARY_PLUS = 0;
    BINARY_MOD = 0;
    BINARY_TIMES = 0;
    BINARY_NE = 0;
    BINARY_SHIFT_LEFT = 0;
    BINARY_SHIFT_RIGHT = 0;
    UNARY_BIT_NOT = 0;
    UNARY_LOGIC_NOT = 0;
    UNARY_MINUS = 0;
    UNARY_NUM_ELTS = 0;
    DATAHANDLING_STORE = 0;
    DATAHANDLING_ASSIGN = 0;
    DATAHANDLING_CALL = 0;
    DATAHANDLING_LOAD = 0;
    DATAHANDLING_LIST_LOAD = 0;
    DATAHANDLING_LIST_STORE = 0;
    FLOWCONTROL_IF = 0;
    FLOWCONTROL_WHILE = 0;
    FLOWCONTROL_CASE = 0;
  }

  std::stringstream streamHeader() {
    std::stringstream stream;

    stream << "\"actor\" : "
           << "\"" << actor << "\", ";
    stream << "\"action\" : "
           << "\"" << action << "\", ";
    stream << "\"firing\" : "
           << "\"" << firingId << "\", ";
    stream << "\"fsm\" : "
           << "true";

    return stream;
  }

  std::stringstream streamConsume() {
    std::stringstream stream;

    if (!iPortRate.empty()) {
      stream << "\"consume\" : [";
      for (auto iter = iPortRate.begin(); iter != iPortRate.end(); iter++) {
        stream << "{ \"port\" :"
               << "\"" << iter->first << "\","
               << "\"count\" : " << iter->second << "}";
        if (std::next(iter) == iPortRate.end()) {
          stream << ",";
        }
      }
      stream << "]";
    }

    return stream;
  }

  std::stringstream streamProduce() {
    std::stringstream stream;

    if (!oPortRate.empty()) {
      stream << "\"produce\" : [";
      for (auto iter = oPortRate.begin(); iter != oPortRate.end(); iter++) {
        stream << "{ \"port\" :"
               << "\"" << iter->first << "\","
               << "\"count\" : " << iter->second << "}";
        if (std::next(iter) == oPortRate.end()) {
          stream << ",";
        }
      }
      stream << "]";
    }

    return stream;
  }

  std::stringstream streamOpCounters() {
    std::stringstream stream;

    bool nextNeedsComa = false;

    stream << "\"op\" : [ ";
    if (BINARY_BIT_AND > 0) {
      stream << "{ \"name\" : \"BINARY_BIT_AND\","
             << "\"count\" : " << BINARY_BIT_AND << "}";
      nextNeedsComa = true;
    }

    if (BINARY_BIT_OR > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_BIT_OR\","
             << "\"count\" : " << BINARY_BIT_OR << "}";
      nextNeedsComa = true;
    }

    if (BINARY_BIT_XOR > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_BIT_XOR\","
             << "\"count\" : " << BINARY_BIT_XOR << "}";
      nextNeedsComa = true;
    }

    if (BINARY_DIV > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_DIV\","
             << "\"count\" : " << BINARY_DIV << "}";
      nextNeedsComa = true;
    }

    if (BINARY_DIV_INT > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_DIV_INT\","
             << "\"count\" : " << BINARY_DIV_INT << "}";
      nextNeedsComa = true;
    }

    if (BINARY_EQ > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_EQ\","
             << "\"count\" : " << BINARY_EQ << "}";
      nextNeedsComa = true;
    }

    if (BINARY_EXP > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_EXP\","
             << "\"count\" : " << BINARY_EXP << "}";
      nextNeedsComa = true;
    }

    if (BINARY_GT > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_GT\","
             << "\"count\" : " << BINARY_GT << "}";
      nextNeedsComa = true;
    }

    if (BINARY_GE > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_GE\","
             << "\"count\" : " << BINARY_GE << "}";
      nextNeedsComa = true;
    }

    if (BINARY_LT > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_LT\","
             << "\"count\" : " << BINARY_LT << "}";
      nextNeedsComa = true;
    }

    if (BINARY_LE > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_LE\","
             << "\"count\" : " << BINARY_LE << "}";
      nextNeedsComa = true;
    }

    if (BINARY_LOGIC_OR > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_LOGIC_OR\","
             << "\"count\" : " << BINARY_LOGIC_OR << "}";
      nextNeedsComa = true;
    }

    if (BINARY_LOGIC_AND > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_LOGIC_AND\","
             << "\"count\" : " << BINARY_LOGIC_AND << "}";
      nextNeedsComa = true;
    }

    if (BINARY_MINUS > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_MINUS\","
             << "\"count\" : " << BINARY_MINUS << "}";
      nextNeedsComa = true;
    }

    if (BINARY_PLUS > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_PLUS\","
             << "\"count\" : " << BINARY_PLUS << "}";
      nextNeedsComa = true;
    }

    if (BINARY_MOD > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_MOD\","
             << "\"count\" : " << BINARY_MOD << "}";
      nextNeedsComa = true;
    }

    if (BINARY_TIMES > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_TIMES\","
             << "\"count\" : " << BINARY_TIMES << "}";
      nextNeedsComa = true;
    }

    if (BINARY_NE > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_NE\","
             << "\"count\" : " << BINARY_NE << "}";
      nextNeedsComa = true;
    }

    if (BINARY_SHIFT_LEFT > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_SHIFT_LEFT\","
             << "\"count\" : " << BINARY_SHIFT_LEFT << "}";
      nextNeedsComa = true;
    }

    if (BINARY_SHIFT_RIGHT > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"BINARY_SHIFT_RIGHT\","
             << "\"count\" : " << BINARY_SHIFT_RIGHT << "}";
      nextNeedsComa = true;
    }

    if (UNARY_BIT_NOT > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"UNARY_BIT_NOT\","
             << "\"count\" : " << UNARY_BIT_NOT << "}";
      nextNeedsComa = true;
    }

    if (UNARY_LOGIC_NOT > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"UNARY_LOGIC_NOT\","
             << "\"count\" : " << UNARY_LOGIC_NOT << "}";
      nextNeedsComa = true;
    }

    if (UNARY_MINUS > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"UNARY_MINUS\","
             << "\"count\" : " << UNARY_MINUS << "}";
      nextNeedsComa = true;
    }

    if (UNARY_NUM_ELTS > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"UNARY_NUM_ELTS\","
             << "\"count\" : " << UNARY_NUM_ELTS << "}";
      nextNeedsComa = true;
    }

    if (DATAHANDLING_STORE > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"DATAHANDLING_STORE\","
             << "\"count\" : " << DATAHANDLING_STORE << "}";
      nextNeedsComa = true;
    }

    if (DATAHANDLING_ASSIGN > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"DATAHANDLING_ASSIGN\","
             << "\"count\" : " << DATAHANDLING_ASSIGN << "}";
      nextNeedsComa = true;
    }

    if (DATAHANDLING_CALL > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"DATAHANDLING_CALL\","
             << "\"count\" : " << DATAHANDLING_CALL << "}";
      nextNeedsComa = true;
    }

    if (DATAHANDLING_LOAD > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"DATAHANDLING_LOAD\","
             << "\"count\" : " << DATAHANDLING_LOAD << "}";
      nextNeedsComa = true;
    }

    if (DATAHANDLING_LIST_LOAD > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"DATAHANDLING_LIST_LOAD\","
             << "\"count\" : " << DATAHANDLING_LIST_LOAD << "}";
      nextNeedsComa = true;
    }

    if (DATAHANDLING_LIST_STORE > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"DATAHANDLING_LIST_STORE\","
             << "\"count\" : " << DATAHANDLING_LIST_STORE << "}";
      nextNeedsComa = true;
    }

    if (DATAHANDLING_LIST_STORE > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"DATAHANDLING_LIST_STORE\","
             << "\"count\" : " << DATAHANDLING_LIST_STORE << "}";
      nextNeedsComa = true;
    }

    if (FLOWCONTROL_IF > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"FLOWCONTROL_IF\","
             << "\"count\" : " << FLOWCONTROL_IF << "}";
      nextNeedsComa = true;
    }

    if (FLOWCONTROL_WHILE > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"FLOWCONTROL_WHILE\","
             << "\"count\" : " << FLOWCONTROL_WHILE << "}";
      nextNeedsComa = true;
    }

    if (FLOWCONTROL_CASE > 0) {
      if (nextNeedsComa) {
        stream << ",";
      }
      stream << "{ \"name\" : \"FLOWCONTROL_CASE\","
             << "\"count\" : " << FLOWCONTROL_CASE << "}";
    }

    stream << "] ";

    return stream;
  }

  bool isEmpty() {
    if (BINARY_BIT_AND > 0) {
      return false;
    }

    if (BINARY_BIT_OR > 0) {
      return false;
    }

    if (BINARY_BIT_XOR > 0) {
      return false;
    }

    if (BINARY_DIV > 0) {
      return false;
    }

    if (BINARY_DIV_INT > 0) {
      return false;
    }

    if (BINARY_EQ > 0) {
      return false;
    }

    if (BINARY_EXP > 0) {
      return false;
    }

    if (BINARY_GT > 0) {
      return false;
    }

    if (BINARY_GE > 0) {
      return false;
    }

    if (BINARY_LT > 0) {
      return false;
    }

    if (BINARY_LE > 0) {
      return false;
    }

    if (BINARY_LOGIC_OR > 0) {
      return false;
    }

    if (BINARY_LOGIC_AND > 0) {
      return false;
    }

    if (BINARY_MINUS > 0) {
      return false;
    }

    if (BINARY_PLUS > 0) {
      return false;
    }

    if (BINARY_MOD > 0) {
      return false;
    }

    if (BINARY_TIMES > 0) {
      return false;
    }

    if (BINARY_NE > 0) {
      return false;
    }

    if (BINARY_SHIFT_LEFT > 0) {
      return false;
    }

    if (BINARY_SHIFT_RIGHT > 0) {
      return false;
    }

    if (UNARY_BIT_NOT > 0) {
      return false;
    }

    if (UNARY_LOGIC_NOT > 0) {
      return false;
    }

    if (UNARY_MINUS > 0) {
      return false;
    }

    if (UNARY_NUM_ELTS > 0) {
      return false;
    }

    if (DATAHANDLING_STORE > 0) {
      return false;
    }

    if (DATAHANDLING_ASSIGN > 0) {
      return false;
    }

    if (DATAHANDLING_CALL > 0) {
      return false;
    }

    if (DATAHANDLING_LOAD > 0) {
      return false;
    }

    if (DATAHANDLING_LIST_LOAD > 0) {
      return false;
    }

    if (DATAHANDLING_LIST_STORE > 0) {
      return false;
    }

    if (DATAHANDLING_LIST_STORE > 0) {
      return false;
    }

    if (FLOWCONTROL_IF > 0) {
      return false;
    }

    if (FLOWCONTROL_WHILE > 0) {
      return false;
    }

    if (FLOWCONTROL_CASE > 0) {
      return false;
    }

    return true;
  }

private:
  std::string actor;
  std::string action;
  long long firingId;

  std::map<std::string, int> iPortRate;
  std::map<std::string, int> oPortRate;
};
