package br.edu.ifba.saj.monitor.service;
import br.edu.ifba.saj.protocolo.*;
import com.google.protobuf.Empty;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.util.function.Consumer;

public class MonitorService {
    private final ManagedChannel channel;
    public MonitorService() { this.channel = ManagedChannelBuilder.forAddress("localhost", 50050).usePlaintext().build(); }
    public void inscreverParaEstadoGeral(Consumer<EstadoGeral> onUpdate) {
        MonitoramentoGrpc.newStub(channel).inscreverParaEstadoGeral(Empty.newBuilder().build(), new StreamObserver<>() {
            public void onNext(EstadoGeral estado) { onUpdate.accept(estado); }
            public void onError(Throwable t) { System.err.println("Erro no stream de monitoramento: " + t.getMessage()); }
            public void onCompleted() {}
        });
    }
    public void shutdown() { channel.shutdown(); }
}