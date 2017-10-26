/*
 * Copyright (C) 2014, United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The Java Pathfinder core (jpf-core) platform is licensed under the
 * Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0. 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package gov.nasa.jpf;

public class MethodCounter {
  public MethodCounter(String methodName){
    this.methodName = methodName;
    totalCalls = 0;
    runningAlone = 0;
    recorded = false;
    readCount = 0;
    writeCount = 0;
    instructionCount = 0;
    argsMatchCount = 0;

    interruptedByNativeCall = false;
    interruptedByTransition = false;
    interruptingMethod = "";

  }

  public boolean interrupted() {
    return interruptedByNativeCall || interruptedByTransition;
  }

  @Override
  public String toString() {
    String str = "{\"methodName\":\"" + methodName + "\"";
    str += ",\"totalCalls\":"+totalCalls;
    str += ",\"readCount\":"+readCount;
    str += ",\"writeCount\":"+writeCount;
    str += ",\"instructionCount\":" + instructionCount;
    str += ",\"interruptedByNativeCall\":" + interruptedByNativeCall;
    str += ",\"interruptedByTransition\":" + interruptedByTransition;
    str += ",\"argsMatchCount\":" + argsMatchCount;
    str += "}";
    return str;
  }
  
  public String toReadableString() {
    String str = "called " + totalCalls + " times";
    if(readCount > 0)
      str += "\nreadCount=" + readCount;
    if(writeCount > 0)
      str += "\nwriteCount=" + writeCount;

    str += "\ninstructionCount=" + instructionCount;

    if(interruptedByNativeCall){
      str += "\nINTERRUPTED BY " + interruptingMethod;
    } else if(interruptedByTransition) {
      str += "\nINTERRUPTED BY TRANSITION";
    }

    if(argsMatchCount != 0) {
      str += "\nArgs matched " + argsMatchCount + " times";
    }

    return str;
  }
  public String methodName;

  public int totalCalls;
  
  // the number of times the method is called 
  // when there's only a single thread.
  public int runningAlone;
  
  // true if it ever finishes a call-return
  // withing the same transition
  public boolean recorded; 
  public boolean interruptedByNativeCall;
  public boolean interruptedByTransition;
  public String interruptingMethod;

  public int readCount;
  public int writeCount;
  public int instructionCount;


  public int argsMatchCount;
}