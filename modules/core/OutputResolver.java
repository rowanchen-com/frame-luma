package frameluma.core;

import java.util.*;

/**
 * Select and verify an output without changing its lighting state. Registry order is irrelevant.
 */
public final class OutputResolver {
    public interface Opener {
        LightOutput open() throws Exception;
    }

    public static final class Candidate {
        public final String moduleId, endpointId, name;
        public final LightCapabilities capabilities;
        private final Opener opener;

        public Candidate(
                String moduleId,
                String endpointId,
                String name,
                LightCapabilities capabilities,
                Opener opener) {
            this.moduleId = moduleId;
            this.endpointId = endpointId;
            this.name = name;
            this.capabilities = capabilities;
            this.opener = opener;
        }
    }

    public static final class Connection {
        public final Candidate candidate;
        public final LightOutput output;
        public final LightIdentity identity;

        private Connection(Candidate c, LightOutput o, LightIdentity i) {
            candidate = c;
            output = o;
            identity = i;
        }
    }

    private OutputResolver() {}

    public static Connection connect(List<Candidate> all) throws Exception {
        List<Candidate> candidates = new ArrayList<>();
        Set<String> endpoints = new HashSet<>();
        for (Candidate candidate : all) {
            candidates.add(candidate);
            endpoints.add(candidate.endpointId);
        }
        if (candidates.isEmpty()) throw new Exception("没有找到灯光设备");
        if (endpoints.size() != 1) throw new Exception("检测到多个灯光控制盒，请保留需要使用的连接");
        if (candidates.size() == 1) return verify(candidates.get(0));
        List<Candidate> verified = new ArrayList<>();
        for (Candidate candidate : candidates) {
            Connection probe = null;
            try {
                probe = verify(candidate);
                verified.add(candidate);
            } catch (Exception ignored) {
                /* A bridge match is only a candidate. */
            } finally {
                if (probe != null) probe.output.close();
            }
        }
        if (verified.size() != 1)
            throw new Exception(verified.isEmpty() ? "控制盒身份未通过握手验证" : "灯控接口存在适配冲突");
        return verify(verified.get(0));
    }

    private static Connection verify(Candidate candidate) throws Exception {
        LightOutput output = candidate.opener.open();
        try {
            LightIdentity identity = output.identify();
            if (identity == null) throw new Exception("Missing verified controller identity");
            candidate.capabilities.requireUnits(output.unitCount());
            return new Connection(candidate, output, identity);
        } catch (Exception error) {
            output.close();
            throw error;
        }
    }
}
