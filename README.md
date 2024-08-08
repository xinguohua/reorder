<p>The dynamic part is based on UFO (<a href="https://github.com/xinguohua/UFO">UFO</a>) and includes LLVM instrumentation and prediction components. First, the instrumentation component modifies ThreadSanitizer in LLVM to insert necessary instrumentation into the bytecode file for logging purposes. Next, the prediction tool analyzes the logs recorded by the instrumentation and predicts potential instruction reordering issues.</p>
<p>You can execute these steps by running a script, for example:</p>
<pre><code>./dynamic_run.sh 22-openmpi.c
</code></pre>
