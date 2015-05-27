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
package collaboratory.storage.object.transport;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import collaboratory.storage.object.store.client.upload.ProgressBar;
import collaboratory.storage.object.store.core.model.DataChannel;
import collaboratory.storage.object.store.core.model.Part;

import com.google.api.client.util.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * The default transport for parallel upload
 */
@Slf4j
@AllArgsConstructor
public class ParallelPartObjectTransport implements ObjectTransport {

  private static final int MIN_WORKER = 10;
  private static final long MIN_MEMORY = 1024L * 1024L;

  final protected ObjectStoreServiceProxy proxy;
  final protected int nThreads;
  final protected ProgressBar progress;
  final protected List<Part> parts;
  final protected String objectId;
  final protected String uploadId;
  final protected Mode mode;
  final protected AtomicLong memory;
  final protected int maxUploadDuration;

  protected ParallelPartObjectTransport(RemoteParallelBuilder builder) {

    this.proxy = builder.proxy;
    this.progress = builder.progressBar;
    this.parts = builder.parts;
    this.objectId = builder.objectId;
    this.uploadId = builder.uploadId;
    this.nThreads = builder.nThreads;
    this.memory = new AtomicLong(builder.memory);
    this.maxUploadDuration = builder.maxUploadDuration;
    this.mode = builder.mode;
  }

  @Override
  @SneakyThrows
  public void send(File file) {
    ExecutorService executor = Executors.newFixedThreadPool(nThreads);

    ImmutableList.Builder<Future<Part>> results = ImmutableList.builder();
    progress.start();
    for (final Part part : parts) {
      results.add(executor.submit(new Callable<Part>() {

        @Override
        public Part call() throws Exception {
          FileDataChannel channel = new FileDataChannel(file, part.getOffset(), part.getPartSize(), null);
          if (part.isCompleted()) {
            if (isCorrupted(channel, part, file)) {
              proxy.uploadPart(channel, part, objectId,
                  uploadId);
            }
            progress.updateChecksum(1);
          } else {
            proxy.uploadPart(channel, part, objectId,
                uploadId);
            progress.updateProgress(1);
          }
          progress.incrementByteWritten(part.getPartSize());
          memory.addAndGet(part.getPartSize());
          return part;
        }
      }));
    }

    executor.shutdown();
    executor.awaitTermination(maxUploadDuration, TimeUnit.DAYS);
    try {
      takeCareOfException(results.build());
      proxy.finalizeUpload(objectId, uploadId);
    } catch (Throwable e) {
      progress.end(true);
      throw e;
    }
    progress.end(false);
  }

  // TODO: should remove file parameter
  protected boolean isCorrupted(DataChannel channel, Part part, File file) throws IOException {
    if (channel.isValidMd5(part.getMd5())) {
      return false;
    }
    switch (mode) {
    case UPLOAD:
      proxy.deleteUploadPart(objectId, uploadId, part);
      break;
    case DOWNLOAD:
      proxy.deleteDownloadPart(file, objectId, part);

    }
    channel.reset();
    return true;
  }

  protected <T> void takeCareOfException(Collection<Future<T>> results) throws Throwable {
    for (Future<T> result : results) {
      try {
        result.get();
      } catch (ExecutionException e) {
        log.debug("Fail on the part", e.getCause());
        throw e.getCause();
      }
    }
  }

  protected void getReadSpeed() {

  }

  protected int getCapacity() {
    return 10;
  }

  public static RemoteParallelBuilder builder() {
    return new RemoteParallelBuilder();
  }

  public static class RemoteParallelBuilder extends ObjectTransport.AbstractBuilder {

    private int nThreads;
    private long memory;
    private int maxUploadDuration;

    public RemoteParallelBuilder withNumberOfWorkerThreads(int threads) {
      this.nThreads = threads;
      return this;
    }

    public RemoteParallelBuilder withMemory(long memory) {
      this.memory = memory;
      return this;
    }

    public RemoteParallelBuilder withMaximumUploadDuration(int duration) {
      this.maxUploadDuration = duration;
      return this;
    }

    @Override
    public ObjectTransport build() {
      checkArgumentsNotNull();
      return new ParallelPartObjectTransport(this);
    }

    protected void checkArgumentsNotNull() {
      Preconditions.checkNotNull(parts);
      Preconditions.checkNotNull(proxy);
      Preconditions.checkNotNull(objectId);
      Preconditions.checkNotNull(uploadId);
      Preconditions.checkNotNull(progressBar);
      Preconditions.checkNotNull(mode);

      nThreads = nThreads < MIN_WORKER ? MIN_WORKER : nThreads;
      memory = memory < MIN_MEMORY ? MIN_MEMORY : memory;
      maxUploadDuration = maxUploadDuration < 1 ? Integer.MAX_VALUE : maxUploadDuration;

    }
  }

  @Override
  public void receive(File file) {
    throw new AssertionError("Please implement it");

  }
}