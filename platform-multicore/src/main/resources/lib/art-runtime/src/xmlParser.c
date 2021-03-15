/* 
 * Copyright (c) Ericsson AB, 2009, EPFL VLSC, 2019
 * Author: Charles Chen Xu (charles.chen.xu@ericsson.com)
 * Author: Endri Bezati (endri.bezati@epfl.ch)
 * All rights reserved.
 *
 * License terms:
 *
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met:
 *     * Redistributions of source code must retain the above 
 *       copyright notice, this list of conditions and the 
 *       following disclaimer.
 *     * Redistributions in binary form must reproduce the 
 *       above copyright notice, this list of conditions and 
 *       the following disclaimer in the documentation and/or 
 *       other materials provided with the distribution.
 *     * Neither the name of the copyright holder nor the names 
 *       of its contributors may be used to endorse or promote 
 *       products derived from this software without specific 
 *       prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT 
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR 
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS 
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <libxml/parser.h>

#include "xmlParser.h"
#include "internal.h"


#define CONFIGURATION                       (const xmlChar*)"configuration"
#define CONNECTION                          (const xmlChar*)"connection"
#define DST                                 (const xmlChar*)"target"
#define DST_PORT                            (const xmlChar*)"target-port"
#define SRC                                 (const xmlChar*)"source"
#define SRC_PORT                            (const xmlChar*)"source-port"
#define FIFO_SIZE                           (const xmlChar*)"size"
#define INSTANCE                            (const xmlChar*)"instance"
#define INSTANCE_NAME                       (const xmlChar*)"id"
#define PARTITION_ID                        (const xmlChar*)"id"
#define SCHEDULING_TYPE                     (const xmlChar*)"scheduling"


void parsePartitioning(xmlNode *node);

void parseConnections(xmlNode *node);

void parsePartition(xmlNode *node);

void parseConnection(xmlNode *node);

typedef void (*HANDLER)(xmlNode *cur_node);

typedef struct _tagID {
    char *name;
    HANDLER handler;
} TagID;

TagID configTag[] = {
        {"partitioning", parsePartitioning},
        {"connections", parseConnections},
        {0}
};

TagID partitioningTag[] = {
        {"partition",  parsePartition},
        {0}
};

TagID connectionsTag[] = {
        {"connection", parseConnection},
        {0}
};

AffinityID instanceAfinity[MAX_ACTOR_NUM];
ConnectID connects[MAX_CONNECTS];
ScheduleID schedule;
int numPartitions = 1;

static int _numInstances;
static int _numConnects;

void printout_config() {
    int i, j;

    printf("Schedule type:       %s\n", schedule.type);
    printf("Number of partition: %d\n", numPartitions);
    printf("partition InstanceName\n");
    for (i = 0; i < _numInstances; i++)
        printf("%d  %s\n", instanceAfinity[i].affinity,
               instanceAfinity[i].name);
    printf("source source_port target target_port size\n");
    for (i = 0; i < _numConnects; i++)
        printf("%s %s %s %s %d\n", connects[i].src,
               connects[i].src_port,
               connects[i].dst,
               connects[i].dst_port,
               connects[i].size);
}

void parseNode(xmlNode *node, TagID *tagID) {
    xmlNode *cur_node;

    for (cur_node = node->children; cur_node != NULL; cur_node = cur_node->next) {
        if (cur_node->type == XML_ELEMENT_NODE) {
            TagID *tag = tagID;
            for (; tag; tag++) {
                if (!tag->name)
                    break;
                if (!xmlStrcmp(cur_node->name, (const xmlChar *) tag->name)) {
                    if (tag->handler)
                        tag->handler(cur_node);
                    break;
                }
            }
        }
    }
}

void parseConnection(xmlNode *cur_node) {
    char *size;

    if (!xmlStrcmp(cur_node->name, CONNECTION))
    {
        if (_numConnects >= MAX_CONNECTS) {
            printf("Number of connections over max allowed (%d)\n", MAX_CONNECTS);
            return;
        }
        connects[_numConnects].dst = (char *) xmlGetProp(cur_node, DST);
        connects[_numConnects].dst_port = (char *) xmlGetProp(cur_node, DST_PORT);
        connects[_numConnects].src = (char *) xmlGetProp(cur_node, SRC);
        connects[_numConnects].src_port = (char *) xmlGetProp(cur_node, SRC_PORT);
        size = (char *) xmlGetProp(cur_node, FIFO_SIZE);
        if (size)
            connects[_numConnects].size = atoi(size);

        if (connects[_numConnects].dst &&
            connects[_numConnects].dst_port &&
            size)
            _numConnects++;

        xmlFree(size);
    }
}

void parsePartition(xmlNode *node) {
    xmlNode *child_node;
    char *id;

    id = (char *) xmlGetProp(node, PARTITION_ID);
    schedule.type = (char *) xmlGetProp(node, SCHEDULING_TYPE);

    for (child_node = node->children; child_node != NULL; child_node = child_node->next) {
        if (child_node->type == XML_ELEMENT_NODE &&
            !xmlStrcmp(child_node->name, INSTANCE)) {
            if (_numInstances >= MAX_ACTOR_NUM) {
                printf("Number of actor instances over max allowed (%d)\n", MAX_ACTOR_NUM);
                return;
            }
            instanceAfinity[_numInstances].name = (char *) xmlGetProp(child_node, INSTANCE_NAME);
            instanceAfinity[_numInstances].affinity = atoi(id);
            _numInstances++;
        }
    }
    numPartitions++;
    xmlFree(id);
}

void parsePartitioning(xmlNode *node) {
    numPartitions = 0;
    parseNode(node, partitioningTag);
}

void parseConnections(xmlNode *node) {
    parseNode(node, connectionsTag);
}


void xmlCleanup(xmlDocPtr doc) {
    /*free the document */
    xmlFreeDoc(doc);

    /*
     *Free the global variables that may
     *have been allocated by the parser.
     */
    xmlCleanupParser();
}

int xmlParser(char *filename, int numInstances) {
    xmlDocPtr doc;

    if (!filename) {
        printf("error: missing config filename\n");
        return -1;
    }

    doc = xmlParseFile(filename);

    if (doc == NULL) {
        printf("error: could not parse file %s\n", filename);
        return -1;
    }

    // --------------------------------------------------------------------------
    // XML root.
    // --------------------------------------------------------------------------
    xmlNode *root = NULL;
    root = xmlDocGetRootElement(doc);

    // --------------------------------------------------------------------------
    // Must have root element, a name and the name must be "Configuration"
    // --------------------------------------------------------------------------
    if (!root ||
        !root->name ||
        xmlStrcmp(root->name, CONFIGURATION)) {
        printf("Invalid input file!\n");
        xmlFreeDoc(doc);
        return -1;
    }

    // --------------------------------------------------------------------------
    // Configuration children
    // --------------------------------------------------------------------------
    parseNode(root, configTag);

    printout_config();

    if (_numInstances != numInstances) {
        printf("error: instance number in config file (%d) DOESN'T match network (%d)\n", _numInstances, numInstances);
        _numConnects = -1;
    }

    // --------------------------------------------------------------------------

    xmlCleanup(doc);

    return _numConnects;
}
