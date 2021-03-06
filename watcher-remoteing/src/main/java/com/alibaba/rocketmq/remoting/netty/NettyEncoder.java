/**
 * Copyright (C) 2010-2013 Alibaba Group Holding Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.rocketmq.remoting.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.rocketmq.remoting.common.RemoteHelper;
import com.alibaba.rocketmq.remoting.common.RemoteUtil;
import com.alibaba.rocketmq.remoting.protocol.RemoteCommand;


/**
 * @author shijia.wxr<vintage.wang@gmail.com>
 * @since 2013-7-13
 */
public class NettyEncoder extends MessageToByteEncoder<RemoteCommand> {
    private static final Logger log = LoggerFactory.getLogger(RemoteHelper.RemotingLogName);

    @Override
    public void encode(ChannelHandlerContext ctx, RemoteCommand remoteCommand, ByteBuf out)
            throws Exception {
        try {
            ByteBuffer header = remoteCommand.encodeHeader();
            out.writeBytes(header);
            byte[] body = remoteCommand.getBody();
            if (body != null) {
                out.writeBytes(body);
            }
        } catch (Exception e) {
            log.error("encode exception, " + RemoteHelper.parseChannelRemoteAddr(ctx.channel()), e);
            if (remoteCommand != null) {
                log.error(remoteCommand.toString());
            }
            RemoteUtil.closeChannel(ctx.channel());
        }
    }
}
