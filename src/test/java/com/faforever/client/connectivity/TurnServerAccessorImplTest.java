package com.faforever.client.connectivity;

import com.faforever.client.net.ConnectionState;
import com.faforever.client.relay.ConnectToPeerMessage;
import com.faforever.client.relay.CreatePermissionMessage;
import com.faforever.client.remote.FafService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import org.ice4j.ResponseCollector;
import org.ice4j.StunMessageEvent;
import org.ice4j.StunResponseEvent;
import org.ice4j.TransportAddress;
import org.ice4j.attribute.Attribute;
import org.ice4j.attribute.DataAttribute;
import org.ice4j.attribute.XorMappedAddressAttribute;
import org.ice4j.attribute.XorPeerAddressAttribute;
import org.ice4j.attribute.XorRelayedAddressAttribute;
import org.ice4j.message.ChannelData;
import org.ice4j.message.Message;
import org.ice4j.message.Response;
import org.ice4j.stack.MessageEventHandler;
import org.ice4j.stack.StunStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.stubbing.Stubber;
import org.springframework.context.ApplicationContext;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.ice4j.Transport.UDP;
import static org.ice4j.attribute.Attribute.XOR_MAPPED_ADDRESS;
import static org.ice4j.attribute.Attribute.XOR_RELAYED_ADDRESS;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TurnServerAccessorImplTest extends AbstractPlainJavaFxTest {

  private static final String TURN_HOST = "example.com";
  private static final int TURN_PORT = 1337;

  private TurnServerAccessorImpl instance;

  @Mock
  private FafService fafService;
  @Mock
  private ScheduledExecutorService scheduledExecutorService;
  @Mock
  private ConnectivityService connectivityService;
  @Mock
  private ApplicationContext applicationContext;
  @Mock
  private StunStack stunStack;
  @Captor
  private ArgumentCaptor<Consumer<CreatePermissionMessage>> createPermissionListenerCaptor;
  @Captor
  private ArgumentCaptor<Consumer<ConnectToPeerMessage>> connectToPeerMessageListenerCaptor;
  @Captor
  private ArgumentCaptor<MessageEventHandler> indicationListenerCaptor;

  @Before
  public void setUp() throws Exception {
    instance = new TurnServerAccessorImpl();
    instance.scheduledExecutorService = scheduledExecutorService;
    instance.fafService = fafService;
    instance.turnHost = TURN_HOST;
    instance.turnPort = TURN_PORT;
    instance.connectivityService = connectivityService;
    instance.applicationContext = applicationContext;

    when(connectivityService.getExternalSocketAddress()).thenReturn(InetSocketAddress.createUnresolved("foo", 123));
    when(applicationContext.getBean(StunStack.class)).thenReturn(stunStack);

    instance.postConstruct();

    verify(fafService).addOnMessageListener(eq(CreatePermissionMessage.class), createPermissionListenerCaptor.capture());
  }

  @After
  public void tearDown() throws Exception {
    instance.disconnect();
  }

  @Test
  public void testConnect() throws Exception {
    CountDownLatch refreshRequestLatch = new CountDownLatch(1);
    doAnswer(invocation -> {
      invocation.getArgumentAt(0, Runnable.class).run();
      refreshRequestLatch.countDown();
      return null;
    }).when(scheduledExecutorService).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());

    connect();

    verify(scheduledExecutorService).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
    assertTrue(refreshRequestLatch.await(3, TimeUnit.SECONDS));
  }

  private void connect() throws IOException {
    StunResponseEvent responseEvent = mock(StunResponseEvent.class);
    Response response = mock(Response.class);
    when(response.isSuccessResponse()).thenReturn(true);
    when(responseEvent.getResponse()).thenReturn(response);

    XorRelayedAddressAttribute xorRelayedAddressAttribute = mock(XorRelayedAddressAttribute.class);
    XorMappedAddressAttribute xorMappedAddressAttribute = mock(XorMappedAddressAttribute.class);
    when(response.getAttribute(XOR_RELAYED_ADDRESS)).thenReturn(xorRelayedAddressAttribute);
    when(response.getAttribute(XOR_MAPPED_ADDRESS)).thenReturn(xorMappedAddressAttribute);
    when(xorRelayedAddressAttribute.getAddress()).thenReturn(new TransportAddress("1.2.3.4", 1234, UDP));
    when(xorMappedAddressAttribute.getAddress()).thenReturn(new TransportAddress("4.3.2.1", 1234, UDP));

    respond(responseEvent).when(stunStack).sendRequest(any(), any(), any(TransportAddress.class), any());

    assertThat(instance.getConnectionState(), is(ConnectionState.DISCONNECTED));

    instance.connect();

    assertThat(instance.getConnectionState(), is(ConnectionState.CONNECTED));
  }

  private Stubber respond(StunResponseEvent response) {
    return doAnswer(invocation -> {
      ResponseCollector responseCollector = invocation.getArgumentAt(3, ResponseCollector.class);
      responseCollector.processResponse(response);
      return null;
    });
  }

  @Test
  public void testDisconnect() throws Exception {
    connect();

    InetSocketAddress localSocketAddress = instance.getLocalSocketAddress();

    instance.disconnect();

    verify(stunStack).removeSocket(eq(new TransportAddress(localSocketAddress, UDP)));
    assertThat(instance.getConnectionState(), is(ConnectionState.DISCONNECTED));
  }

  @Test
  public void testDisconnectNotDisconnected() throws Exception {
    assertThat(instance.getConnectionState(), is(ConnectionState.DISCONNECTED));
    instance.disconnect();
    verifyZeroInteractions(stunStack);
    assertThat(instance.getConnectionState(), is(ConnectionState.DISCONNECTED));
  }

  @Test
  public void testSend() throws Exception {
    connect();

    InetSocketAddress remotePeerAddress = new InetSocketAddress("93.184.216.34", 1234);

    CreatePermissionMessage createPermissionMessage = new CreatePermissionMessage();
    createPermissionMessage.setAddress(remotePeerAddress);

    createPermissionListenerCaptor.getValue().accept(createPermissionMessage);

    byte[] bytes = new byte[]{0x00, 0x01, 0x02};
    DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length);
    datagramPacket.setSocketAddress(remotePeerAddress);

    instance.send(datagramPacket);

    ArgumentCaptor<ChannelData> channelDataCaptor = ArgumentCaptor.forClass(ChannelData.class);
    ArgumentCaptor<TransportAddress> sendToCaptor = ArgumentCaptor.forClass(TransportAddress.class);
    ArgumentCaptor<TransportAddress> sendThroughCaptor = ArgumentCaptor.forClass(TransportAddress.class);
    verify(stunStack).sendChannelData(channelDataCaptor.capture(), sendToCaptor.capture(), sendThroughCaptor.capture());

    assertThat(channelDataCaptor.getValue().getChannelNumber(), is(greaterThanOrEqualTo((char) 0x4000)));
    assertThat(channelDataCaptor.getValue().getDataLength(), is((char) 3));
    assertThat(sendToCaptor.getValue(), equalTo(new TransportAddress(TURN_HOST, TURN_PORT, UDP)));
    assertThat(sendThroughCaptor.getValue(), equalTo(new TransportAddress(instance.getLocalSocketAddress(), UDP)));
  }

  @Test
  public void testReceiveChannelData() throws Exception {
    doAnswer(invocation -> {
      WaitForAsyncUtils.async(invocation.getArgumentAt(0, Runnable.class));
      return null;
    }).when(scheduledExecutorService).execute(any());

    try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLocalHost(), 0))) {
      instance.turnHost = socket.getLocalAddress().getHostAddress();
      instance.turnPort = socket.getLocalPort();
      connect();

      CompletableFuture<DatagramPacket> packetFuture = new CompletableFuture<>();
      instance.addOnPacketListener(packetFuture::complete);

      InetSocketAddress localSocketAddress = instance.getLocalSocketAddress();

      // First two bytes are channel number, second two bytes are message length, rest is data
      byte[] channelData = new byte[]{0x40, 0x00, 0x00, 0x04, 0x01, 0x02, 0x03, 0x04};
      DatagramPacket datagramPacket = new DatagramPacket(channelData, channelData.length);
      datagramPacket.setSocketAddress(localSocketAddress);
      datagramPacket.setData(channelData);


      CreatePermissionMessage createPermissionMessage = new CreatePermissionMessage();
      createPermissionMessage.setAddress((InetSocketAddress) socket.getLocalSocketAddress());
      createPermissionListenerCaptor.getValue().accept(createPermissionMessage);

      socket.send(datagramPacket);

      DatagramPacket packetReceivedOnChannel = packetFuture.get(2, TimeUnit.SECONDS);
      assertArrayEquals(Arrays.copyOfRange(channelData, 4, channelData.length), packetReceivedOnChannel.getData());
    }
  }

  @Test
  public void testIndication() throws Exception {
    connect();
    verify(stunStack).addIndicationListener(any(), indicationListenerCaptor.capture());

    CompletableFuture<DatagramPacket> packetFuture = new CompletableFuture<>();
    instance.addOnPacketListener(packetFuture::complete);

    StunMessageEvent event = mock(StunResponseEvent.class);
    Message message = mock(Message.class);
    when(event.getMessage()).thenReturn(message);

    byte[] data = "\bBind 102144".getBytes(US_ASCII);
    DataAttribute dataAttribute = mock(DataAttribute.class);
    when(dataAttribute.getData()).thenReturn(data);
    when(message.getAttribute(Attribute.DATA)).thenReturn(dataAttribute);

    TransportAddress sender = new TransportAddress("1.2.3.4", 1234, UDP);
    XorPeerAddressAttribute xorPeerAddressAttribute = mock(XorPeerAddressAttribute.class);
    when(xorPeerAddressAttribute.getAddress(any())).thenReturn(sender);
    when(message.getAttribute(Attribute.XOR_PEER_ADDRESS)).thenReturn(xorPeerAddressAttribute);

    indicationListenerCaptor.getValue().handleMessageEvent(event);

    DatagramPacket packet = packetFuture.get(2, TimeUnit.SECONDS);
    assertThat(packet.getData(), is(data));
    assertThat(packet.getSocketAddress(), is(sender));
  }
}
