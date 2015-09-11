/*
 * Copyright (c) 2015 The Ontario Institute for Cancer Research. All rights reserved.                             
 *                                                                                                               
 * This program and the accompanying materials are made available under the terms of the GNU Public License v3.0.
 * You should have received a copy of the GNU General Public License along with                                  
 * this program. If not, see <http://www.gnu.org/licenses/>.                                                     
 *                                                                                                               
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY                           
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES                          
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT                           
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,                                
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED                          
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;                               
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER                              
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN                         
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package collaboratory.storage.object.store.client.slicing;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterators;

/**
 * 
 */
public class QueryParser {

  public static List<Slice> parse(List<String> queries) {
    List<Slice> result = new ArrayList<Slice>();
    for (String region : queries) {
      result.add(parse(region));
    }
    return result;
  }

  public static Slice parse(String query) {
    Iterable<String> tokens = Splitter.on(CharMatcher.anyOf(":-")).omitEmptyStrings().trimResults().split(query);
    Iterator<String> it = tokens.iterator();
    int count = Iterators.size(tokens.iterator());

    Slice result = null;
    try {
      switch (count) {
      case 1:
        result = new Slice(it.next());
        break;
      case 2:
        result = new Slice(it.next(), Integer.parseInt(it.next().replace(",", "")));
        break;
      case 3:
        result =
            new Slice(it.next(), Integer.parseInt(it.next().replace(",", "")), Integer.parseInt(it.next().replace(",",
                "")));
        break;
      default:
        throw new IllegalArgumentException(String.format("Unrecognizable region %s", query));
      }
    } catch (NumberFormatException nfe) {
      throw new IllegalArgumentException(String.format("Invalid region specified %s", query), nfe);
    }
    return result;
  }
}
