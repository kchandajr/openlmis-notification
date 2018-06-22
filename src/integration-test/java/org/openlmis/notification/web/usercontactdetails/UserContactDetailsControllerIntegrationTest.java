/*
 * This program is part of the OpenLMIS logistics management information system platform software.
 * Copyright © 2017 VillageReach
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 * See the GNU Affero General Public License for more details. You should have received a copy of
 * the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org. 
 */

package org.openlmis.notification.web.usercontactdetails;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.openlmis.notification.i18n.MessageKeys.EMAIL_VERIFICATION_SUCCESS;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_EMAIL_DUPLICATED;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_EMAIL_INVALID;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_FIELD_IS_INVARIANT;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_ID_MISMATCH;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_TOKEN_EXPIRED;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_TOKEN_INVALID;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_USER_CONTACT_DETAILS_NOT_FOUND;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_USER_EMAIL_ALREADY_VERIFIED;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_USER_HAS_NO_EMAIL;
import static org.openlmis.notification.i18n.MessageKeys.PERMISSION_MISSING;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.jayway.restassured.response.Response;
import guru.nidi.ramltester.junit.RamlMatchers;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.Before;
import org.junit.Test;
import org.openlmis.notification.domain.EmailVerificationToken;
import org.openlmis.notification.domain.UserContactDetails;
import org.openlmis.notification.i18n.ExposedMessageSource;
import org.openlmis.notification.repository.EmailVerificationTokenRepository;
import org.openlmis.notification.repository.UserContactDetailsRepository;
import org.openlmis.notification.service.EmailVerificationNotifier;
import org.openlmis.notification.service.PermissionService;
import org.openlmis.notification.testutils.EmailVerificationTokenDataBuilder;
import org.openlmis.notification.util.EmailDetailsDataBuilder;
import org.openlmis.notification.util.UserContactDetailsDataBuilder;
import org.openlmis.notification.web.BaseWebIntegrationTest;
import org.openlmis.notification.web.MissingPermissionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@SuppressWarnings({"PMD.TooManyMethods"})
public class UserContactDetailsControllerIntegrationTest extends BaseWebIntegrationTest {

  private static final String RESOURCE_URL = "/api/userContactDetails";
  private static final String ID_RESOURCE_URL = RESOURCE_URL + "/{id}";
  private static final String VERIFICATIONS_URL = ID_RESOURCE_URL + "/verifications";
  private static final String TOKEN_URL = VERIFICATIONS_URL + "/{token}";

  private static final String ID = "id";
  private static final String TOKEN = "token";


  @MockBean
  private UserContactDetailsRepository repository;

  @MockBean
  private PermissionService permissionService;

  @MockBean
  private EmailVerificationTokenRepository emailVerificationTokenRepository;

  @MockBean
  private EmailVerificationNotifier emailVerificationNotifier;

  @Autowired
  private ExposedMessageSource messageSource;

  private UserContactDetails userContactDetails;

  @Before
  public void setUp() {
    userContactDetails = new UserContactDetailsDataBuilder()
        .withEmailDetails(new EmailDetailsDataBuilder().withUnverifiedFlag().build())
        .build();

    given(repository.findOne(userContactDetails.getId()))
        .willReturn(userContactDetails);
  }

  @Test
  public void shouldGetUserContactDetails() {
    willDoNothing()
        .given(permissionService)
        .canManageUserContactDetails(userContactDetails.getReferenceDataUserId());

    get(userContactDetails.getReferenceDataUserId())
        .then()
        .statusCode(200);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());

    verify(repository).findOne(userContactDetails.getReferenceDataUserId());
    verify(permissionService)
        .canManageUserContactDetails(userContactDetails.getReferenceDataUserId());
  }

  @Test
  public void shouldReturnNotFoundWhenTryingToFetchNonExistentUserContactDetails() {
    given(repository.findOne(userContactDetails.getReferenceDataUserId()))
        .willReturn(null);

    get(userContactDetails.getReferenceDataUserId())
        .then()
        .statusCode(404);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());

    verify(repository).findOne(userContactDetails.getReferenceDataUserId());
    verify(permissionService)
        .canManageUserContactDetails(userContactDetails.getReferenceDataUserId());
  }

  @Test
  public void shouldReturnForbiddenWhenTryingToFetchUserContactDetailsWithoutPermissions() {
    willThrow(new MissingPermissionException())
        .given(permissionService)
        .canManageUserContactDetails(userContactDetails.getReferenceDataUserId());

    get(userContactDetails.getReferenceDataUserId())
        .then()
        .statusCode(403);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());

    verify(repository, never()).findOne(any(UUID.class));
    verify(permissionService)
        .canManageUserContactDetails(userContactDetails.getReferenceDataUserId());    
  }

  @Test
  public void shouldCreateUserContactDetails() {
    UserContactDetailsDto request = toDto(userContactDetails);

    given(repository.exists(any(UUID.class))).willReturn(false);
    given(repository.save(any(UserContactDetails.class))).willReturn(userContactDetails);

    UserContactDetailsDto response = put(request)
        .then()
        .statusCode(200)
        .extract()
        .as(UserContactDetailsDto.class);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
    assertEquals(request, response);

    verify(repository).save(userContactDetails);
  }

  @Test
  public void shouldUpdateUserContactDetails() {
    UserContactDetails existing = new UserContactDetailsDataBuilder()
        .withReferenceDataUserId(userContactDetails.getReferenceDataUserId())
        .withEmailDetails(userContactDetails.getEmailDetails())
        .build();

    given(repository.exists(any(UUID.class))).willReturn(true);
    given(repository.findOne(userContactDetails.getReferenceDataUserId())).willReturn(existing);
    given(repository.save(any(UserContactDetails.class))).willReturn(userContactDetails);

    UserContactDetailsDto request = toDto(userContactDetails);
    UserContactDetailsDto response = put(request)
        .then()
        .statusCode(200)
        .extract()
        .as(UserContactDetailsDto.class);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
    assertEquals(request, response);

    verify(repository).save(userContactDetails);
    verify(repository, times(2)).findOne(userContactDetails.getReferenceDataUserId());
  }

  @Test
  public void shouldReturnForbiddenWhenTryingToSaveUserContactDetailsWithoutPermissions() {
    willThrow(new MissingPermissionException())
        .given(permissionService)
        .canManageUserContactDetails(userContactDetails.getReferenceDataUserId());

    put(toDto(userContactDetails))
        .then()
        .statusCode(403);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());

    verify(repository, never()).save(any(UserContactDetails.class));
    verify(permissionService)
        .canManageUserContactDetails(userContactDetails.getReferenceDataUserId());
  }

  @Test
  public void shouldReturnBadRequestWhenTryingToChangeIsEmailVerifiedFlag() {
    given(repository.exists(any(UUID.class))).willReturn(true);
    given(repository.findOne(userContactDetails.getReferenceDataUserId()))
        .willReturn(userContactDetails);

    UserContactDetailsDto request = toDto(userContactDetails);
    request.getEmailDetails().setEmailVerified(!userContactDetails.isEmailAddressVerified());

    String response = put(request)
        .then()
        .statusCode(400)
        .extract()
        .path(MESSAGE_KEY);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
    assertThat(response, containsString(ERROR_FIELD_IS_INVARIANT));

    verify(repository, never()).save(any(UserContactDetails.class));
    verify(permissionService)
        .canManageUserContactDetails(userContactDetails.getReferenceDataUserId());
  }

  @Test
  public void shouldReturnBadRequestWhenTryingToSetEmailThatIsAlreadyInUseByOtherUser() {
    willThrow(new DataIntegrityViolationException("",
        new ConstraintViolationException("", null, "unq_contact_details_email"))
    ).given(repository).save(userContactDetails);

    String response = put(toDto(userContactDetails))
        .then()
        .statusCode(400)
        .extract()
        .path(MESSAGE_KEY);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
    assertThat(response, containsString(ERROR_EMAIL_DUPLICATED));

    verify(repository).save(userContactDetails);
    verify(permissionService)
        .canManageUserContactDetails(userContactDetails.getReferenceDataUserId());
  }

  @Test
  public void shouldReturnBardRequestWhenTryingToSaveUserContactDetailsWithInvalidEmail() {
    userContactDetails.getEmailDetails().setEmail("someDefinitelyInvalidEmail");

    String response = put(toDto(userContactDetails))
        .then()
        .statusCode(400)
        .extract()
        .path(MESSAGE_KEY);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
    assertThat(response, containsString(ERROR_EMAIL_INVALID));

    verify(repository, never()).save(any(UserContactDetails.class));
    verify(permissionService)
        .canManageUserContactDetails(userContactDetails.getReferenceDataUserId());
  }

  @Test
  public void shouldVerifyEmail() {
    EmailVerificationToken token = new EmailVerificationTokenDataBuilder()
        .withContactDetails(userContactDetails)
        .build();

    given(emailVerificationTokenRepository.findOne(token.getId()))
        .willReturn(token);

    String expectedResponse = messageSource.getMessage(
        EMAIL_VERIFICATION_SUCCESS,
        new Object[]{token.getEmailAddress()},
        LocaleContextHolder.getLocale());

    String response = startUserRequest()
        .pathParam(ID, userContactDetails.getId())
        .pathParam(TOKEN, token.getId())
        .given()
        .get(TOKEN_URL)
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .asString();

    assertThat(response, is(expectedResponse));

    assertThat(userContactDetails.getEmailAddress(), is(token.getEmailAddress()));
    assertThat(userContactDetails.isEmailAddressVerified(), is(true));

    verify(repository).save(userContactDetails);
    verify(emailVerificationTokenRepository).delete(token.getId());

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldReturnBadRequestIfTokenDoesNotExist() {
    given(emailVerificationTokenRepository.findOne(any(UUID.class)))
        .willReturn(null);

    startUserRequest()
        .pathParam(ID, userContactDetails.getId())
        .pathParam(TOKEN, UUID.randomUUID())
        .given()
        .get(TOKEN_URL)
        .then()
        .statusCode(HttpStatus.BAD_REQUEST.value())
        .body(MESSAGE_KEY, equalTo(ERROR_TOKEN_INVALID));

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldReturnBadRequestIfTokenExpired() {
    EmailVerificationToken token = new EmailVerificationTokenDataBuilder()
        .withExpiredDate()
        .withContactDetails(userContactDetails)
        .build();

    given(emailVerificationTokenRepository.findOne(token.getId()))
        .willReturn(token);

    startUserRequest()
        .pathParam(ID, userContactDetails.getId())
        .pathParam(TOKEN, token.getId())
        .given()
        .get(TOKEN_URL)
        .then()
        .statusCode(HttpStatus.BAD_REQUEST.value())
        .body(MESSAGE_KEY, equalTo(ERROR_TOKEN_EXPIRED));

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldReturnBadRequestIfIdMismatch() {
    EmailVerificationToken token = new EmailVerificationTokenDataBuilder()
        .withExpiredDate()
        .build();

    given(emailVerificationTokenRepository.findOne(token.getId()))
        .willReturn(token);

    startUserRequest()
        .pathParam(ID, UUID.randomUUID())
        .pathParam(TOKEN, token.getId())
        .given()
        .get(TOKEN_URL)
        .then()
        .statusCode(HttpStatus.BAD_REQUEST.value())
        .body(MESSAGE_KEY, equalTo(ERROR_ID_MISMATCH));

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldGetPendingVerificationEmail() {
    EmailVerificationToken token = new EmailVerificationTokenDataBuilder()
        .withExpiredDate()
        .build();

    given(emailVerificationTokenRepository
        .findOneByUserContactDetails(any(UserContactDetails.class)))
        .willReturn(token);

    startUserRequest()
        .pathParam(ID, userContactDetails.getId())
        .given()
        .get(VERIFICATIONS_URL)
        .then()
        .statusCode(HttpStatus.OK.value());

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldReturnNotFoundIfContactDetailsDoesNotExist() {
    given(repository.findOne(any(UUID.class))).willReturn(null);

    startUserRequest()
        .pathParam(ID, userContactDetails.getId())
        .given()
        .get(VERIFICATIONS_URL)
        .then()
        .statusCode(HttpStatus.NOT_FOUND.value())
        .body(MESSAGE_KEY, is(ERROR_USER_CONTACT_DETAILS_NOT_FOUND));

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldResendVerificationEmail() {
    EmailVerificationToken token = new EmailVerificationTokenDataBuilder().build();

    given(emailVerificationTokenRepository
        .findOneByUserContactDetails(any(UserContactDetails.class)))
        .willReturn(token);
    willDoNothing()
        .given(emailVerificationNotifier)
        .sendNotification(any(UserContactDetails.class), anyString());

    startUserRequest()
        .pathParam(ID, userContactDetails.getId())
        .given()
        .post(VERIFICATIONS_URL)
        .then()
        .statusCode(HttpStatus.OK.value());

    verify(emailVerificationNotifier)
        .sendNotification(any(UserContactDetails.class), eq(token.getEmailAddress()));

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldNotResendVerificationEmailIfUserHasNoPermissions() {
    MissingPermissionException ex = new MissingPermissionException("test");
    willThrow(ex).given(permissionService).canManageUserContactDetails(userContactDetails.getId());

    startUserRequest()
        .pathParam(ID, userContactDetails.getId())
        .given()
        .post(VERIFICATIONS_URL)
        .then()
        .statusCode(HttpStatus.FORBIDDEN.value())
        .body(MESSAGE_KEY, is(PERMISSION_MISSING));

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
    verifyZeroInteractions(emailVerificationNotifier);
  }

  @Test
  public void shouldNotResendVerificationEmailIfUserNotFound() {
    startUserRequest()
        .pathParam(ID, UUID.randomUUID())
        .given()
        .post(VERIFICATIONS_URL)
        .then()
        .statusCode(HttpStatus.NOT_FOUND.value())
        .body(MESSAGE_KEY, equalTo(ERROR_USER_CONTACT_DETAILS_NOT_FOUND));

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
    verifyZeroInteractions(emailVerificationNotifier);
  }

  @Test
  public void shouldNotResendVerificationEmailIfUserHasNoEmail() {
    userContactDetails.getEmailDetails().setEmail(null);

    startUserRequest()
        .pathParam(ID, userContactDetails.getId())
        .given()
        .post(VERIFICATIONS_URL)
        .then()
        .statusCode(HttpStatus.BAD_REQUEST.value())
        .body(MESSAGE_KEY, equalTo(ERROR_USER_HAS_NO_EMAIL));

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
    verifyZeroInteractions(emailVerificationNotifier);
  }

  @Test
  public void shouldNotResendVerificationEmailIfUserEmailHasBeenVerified() {
    userContactDetails.setEmailDetails(new EmailDetailsDataBuilder().withVerified(true).build());

    startUserRequest()
        .pathParam(ID, userContactDetails.getId())
        .given()
        .post(VERIFICATIONS_URL)
        .then()
        .statusCode(HttpStatus.BAD_REQUEST.value())
        .body(MESSAGE_KEY, equalTo(ERROR_USER_EMAIL_ALREADY_VERIFIED));

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
    verifyZeroInteractions(emailVerificationNotifier);
  }

  private Response put(UserContactDetailsDto dto) {
    return startUserRequest()
        .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
        .body(dto)
        .given()
        .pathParam(ID, dto.getReferenceDataUserId())
        .put(ID_RESOURCE_URL);
  }

  private Response get(UUID referenceDataUserId) {
    return startUserRequest()
        .contentType(APPLICATION_JSON_VALUE)
        .pathParam(ID, referenceDataUserId)
        .given()
        .get(ID_RESOURCE_URL);
  }

  private UserContactDetailsDto toDto(UserContactDetails userContactDetails) {
    UserContactDetailsDto dto = new UserContactDetailsDto();
    userContactDetails.export(dto);
    return dto;
  }

}
