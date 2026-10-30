package org.example.pedidos.api;

import org.example.pedidos.application.ConflitoIdempotenciaException;
import org.example.pedidos.application.PrecondicaoVersaoException;
import org.example.pedidos.application.RecursoNaoEncontradoException;
import org.example.pedidos.domain.TransicaoPedidoInvalidaException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "Dados inválidos", detail);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> parameterValidation(HandlerMethodValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Parâmetros inválidos", "Revise os parâmetros informados.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadableBody(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "JSON inválido",
                "O corpo deve ser um JSON válido e corresponder ao formato esperado.");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> missingHeader(MissingRequestHeaderException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Cabeçalho obrigatório",
                "O cabeçalho " + exception.getHeaderName() + " é obrigatório.");
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ProblemDetail> notFound(RecursoNaoEncontradoException exception) {
        return problem(HttpStatus.NOT_FOUND, "Recurso não encontrado", exception.getMessage());
    }

    @ExceptionHandler({ConflitoIdempotenciaException.class, TransicaoPedidoInvalidaException.class})
    public ResponseEntity<ProblemDetail> conflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Conflito", exception.getMessage());
    }

    @ExceptionHandler({PrecondicaoVersaoException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetail> preconditionFailed(RuntimeException exception) {
        return problem(HttpStatus.PRECONDITION_FAILED, "Versão desatualizada",
                "O pedido foi alterado por outra operação; consulte-o e tente novamente com a versão atual.");
    }

    @ExceptionHandler(PreconditionRequiredException.class)
    public ResponseEntity<ProblemDetail> preconditionRequired(PreconditionRequiredException exception) {
        return problem(HttpStatus.PRECONDITION_REQUIRED, "Pré-condição obrigatória", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> badRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Requisição inválida", exception.getMessage());
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        body.setType(URI.create("about:blank"));
        return ResponseEntity.status(status).body(body);
    }
}
