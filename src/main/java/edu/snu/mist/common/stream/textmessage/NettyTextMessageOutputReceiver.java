/*
 * Copyright (C) 2018 Seoul National University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.snu.mist.common.stream.textmessage;

import edu.snu.mist.common.stream.NettyChannelHandler;
import edu.snu.mist.common.stream.OutputReceiver;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.apache.reef.tang.exceptions.InjectionException;
import org.apache.reef.wake.impl.DefaultThreadFactory;

import java.net.InetSocketAddress;

/**
 * This class receives output data stream from queries using Netty.
 */
public final class NettyTextMessageOutputReceiver implements OutputReceiver<String> {
  private static final String CLASS_NAME = NettyTextMessageStreamGenerator.class.getName();
  private static final int SERVER_BOSS_NUM_THREADS = 3;
  private static final int SERVER_WORKER_NUM_THREADS = 10;

  private final ChannelGroup serverChannelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
  private final EventLoopGroup serverBossGroup;
  private final EventLoopGroup serverWorkerGroup;
  private final Channel acceptor;

  public NettyTextMessageOutputReceiver(final String address,
                                        final int serverPort)
      throws InjectionException, InterruptedException {
    this(address, serverPort, null,
        SERVER_BOSS_NUM_THREADS, SERVER_WORKER_NUM_THREADS);
  }

  public NettyTextMessageOutputReceiver(final String address,
                                        final int serverPort,
                                        final NettyChannelHandler channelHandler)
      throws InjectionException, InterruptedException {
    this(address, serverPort, channelHandler,
        SERVER_BOSS_NUM_THREADS, SERVER_WORKER_NUM_THREADS);
  }


  public NettyTextMessageOutputReceiver(final String address,
                                        final int serverPort,
                                        final NettyChannelHandler channelHandler,
                                        final int numBossThreads,
                                        final int numWorkerThreads)
      throws InjectionException, InterruptedException {
    this.serverBossGroup = new NioEventLoopGroup(numBossThreads,
        new DefaultThreadFactory(CLASS_NAME + "SinkServerBoss"));
    this.serverWorkerGroup = new NioEventLoopGroup(numWorkerThreads,
        new DefaultThreadFactory(CLASS_NAME + "SinkServerWorker"));
    final ServerBootstrap serverBootstrap = new ServerBootstrap();
    serverBootstrap.group(this.serverBossGroup, this.serverWorkerGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(new NettyTextMessageChannelInitializer(() ->
            new NettyTextMessageStreamChannelHandler(serverChannelGroup, channelHandler)))
        .option(ChannelOption.SO_BACKLOG, 128)
        .option(ChannelOption.SO_REUSEADDR, true)
        .childOption(ChannelOption.SO_KEEPALIVE, true);
    this.acceptor = serverBootstrap.bind(
        new InetSocketAddress(address, serverPort)).sync().channel();
  }

  @Override
  public void close() throws Exception {
    serverChannelGroup.close().awaitUninterruptibly();
    acceptor.close().sync();
    serverBossGroup.shutdownGracefully();
    serverWorkerGroup.shutdownGracefully();
  }
}
