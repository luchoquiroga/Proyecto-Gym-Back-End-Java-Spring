package com.gimnasio.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClienteLoginResponse {

    private String mensaje;
    private String accessToken;
    private ClienteResponse cliente;
}
