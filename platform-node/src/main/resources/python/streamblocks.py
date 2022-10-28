# Copyright (c) Ericsson AB, 2013
# Author: Patrik Persson (patrik.j.persson@ericsson.com)
# All rights reserved.
#
# License terms:
#
# Redistribution and use in source and binary forms,
# with or without modification, are permitted provided
# that the following conditions are met:
#    # Redistributions of source code must retain the above
#       copyright notice, this list of conditions and the
#       following disclaimer.
#    # Redistributions in binary form must reproduce the
#       above copyright notice, this list of conditions and
#       the following disclaimer in the documentation and/or
#       other materials provided with the distribution.
#    # Neither the name of the copyright holder nor the names
#       of its contributors may be used to endorse or promote
#       products derived from this software without specific
#       prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
# CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
# INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
# CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
# NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
# LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
# HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
# CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
# OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
# SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
# ------------------------------------------------------------

import socket
import sys

# =============================================================================
# Python client bindings for supervision of Calvin nodes.
# -----------------------------------------------------------------------------

class Node:
  """Represents a remote Calvin node."""

  # ---------------------------------------------------------------------------

  class Port:
    """Represents a port on an actor."""
    listening_port = 0

    def __init__(self, name, node, actor):
      self.name = name
      self.node = node
      self.actor = actor

    def connectToInput(self, i):
      """Make a connection. Can only be called for outputs."""
      if False and self.node == i.node:
        self.node.execute("CONNECT %s.%s %s.%s" % (self.actor.name, self.name, i.actor.name, i.name))
      else:
        listening_port = i.node.execute("LISTEN %s.%s" % (i.actor.name, i.name))
        self.node.execute("CONNECT %s.%s %s:%s" % (self.actor.name, self.name, i.node.address, listening_port))
        self.listening_port = listening_port

    def __lshift__(self, o): o.connectToInput(self)
    def __rshift__(self, i): self.connectToInput(i)

    def disconnectFromInput(self, i) :
        if False and self.node == i.node :
            self.node.execute("DISCONNECT %s.%s %s.%s" %
                    (self.actor.name, self.name, i.actor.name, i.name))
        else :
            self.node.execute("DISCONNECT %s.%s %s:%s" %
                    (self.actor.name, self.name, i.node.address, self.listening_port))

  # ---------------------------------------------------------------------------

  class Actor:
    """Represents an actor on a remote Calvin node."""
    def __init__(self, node, name):
      self.name = name
      self.node = node
      for port in self.ports():
        _, name, _ = port.split(":")
        self.__dict__[name] = Node.Port(name, node, self)

    def check(self):
      for port in self.ports():
        tp, name, nbr = port.split(":")
        if nbr == '-':
          print("port not connected: %s.%s" % (self.name, name))
          # raise RuntimeError("port not connected: %s.%s" % (self.name, name))
        elif tp == 'i' and int(nbr) != 0:
          print("input not empty: %s.%s (%s tokens)" % (self.name, name, nbr))
        elif tp == 'o' and int(nbr) != 64:
          print("output not empty: %s.%s (%s slots)" % (self.name, name, nbr))


    def enable(self):
      self.node.execute("ENABLE %s" % self.name)

    def disable(self):
        self.node.execute("DISABLE %s" % self.name)

    def serialize(self):
        return self.node.execute("SERIALIZE %s" % self.name)

    def deserialize(self, closure):
        self.node.execute("DESERIALIZE %s %s" % (self.name, closure) )

    def destroy(self):
      self.node.execute("DESTROY %s" % self.name)

    def ports(self):
      return self.node.execute("SHOW %s" % self.name).split(" ")[1:]

  # ---------------------------------------------------------------------------

  def __init__(self, host, port, verbose = True):
    sock = socket.socket()
    sock.connect((host, port))
    self.conn = sock.makefile(mode='rw')
    self.verbose = verbose
    self.actors = []
    self.get_result()  # TODO: check version ID returned by server here
    self.address = self.get_address()

  def get_result(self):
    status = self.conn.readline().strip().split(" ", 1)
    if status[0] == "ERROR":
      raise RuntimeError("error: %s " % status[1])
    elif status[0] != "OK":
      raise RuntimeError("invalid response: %s" % status)
    self.conn.read(2) # consume and ignore prompt
    return status[1]

  def get_address(self):
    # TODO: be more clever about which IP address to choose
    return self.execute("ADDRESS").split(" ")[0]

  def execute(self, command):
    if self.verbose: print("--> %s" % command)
    self.conn.write(command)
    self.conn.write("\n")
    self.conn.flush()
    result = self.get_result()
    if self.verbose: print("<-- OK %s" % result)
    return result

  def load(self, file_name): return self.execute("LOAD %s" % file_name)

  def new(self, _class_name, _instance_name = None, **args):
    instance_name = _class_name
    if _instance_name:
      instance_name = _instance_name
    serialized_args = " ".join(["%s=\"%s\"" % (k,v) for (k,v) in list(args.items())])
    self.execute("NEW %s %s %s" % (_class_name, instance_name, serialized_args))
    actor = Node.Actor(self, instance_name)
    self.actors.append(actor)
    return actor

  def destroyAll(self):
    """Destroys all actors created from this Node."""
    for actor in self.actors:
        actor.destroy()

  def join(self): self.execute("JOIN")

