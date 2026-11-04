package nexus.io.tio.chat.service.base.msg;

/**
 * 一个消息渠道，由 def_interface.impl_class 选择
 */
public interface MsgStrategy {

  MsgResult exec(MsgParam param) throws Exception;
}
